# opencode-agents

A self-hosted Slack bot that connects Slack conversations to an [opencode](https://opencode.ai) agent server. Mention the bot in a channel or send it a message, and it runs the configured opencode agent against your repositories with GitHub/Javadoc MCP access, then posts the answer back into the Slack thread complete with duration, tool-call count and estimated cost.

## Features

- **Slack-native workflow** - app mentions and messages in configured channels are routed to opencode, with a `/oc` slash command for quick checks.
- **One session per Slack thread** - mentioning the bot again in a thread continues the same opencode session so the agent keeps its context.
- **Safe concurrency** - a distributed Redis lock per thread prevents overlapping prompts that would steer each other.
- **Managed opencode server** - spawns and supervises `opencode serve`, performs health checks and restarts it with exponential backoff when it crashes.
- **Stall detection** - a watchdog monitors session activity and SSE heartbeats, reconciles missed events and restarts wedged servers.
- **Cost tracking** - estimates turn cost from model list prices when the provider does not report one.
- **Optional Git config sync** - clones the opencode configuration repository over SSH on startup.
- **Multi-arch Docker image** - built for `linux/amd64` and `linux/arm64` in CI.

## Architecture

```
Slack
  │  HTTP events
  ▼
Ktor :8080  /slack/events ──proxy──▶  Slack Bolt :3000  /slack/events
                                          │
                                          │ publish "slack_messages"
                                          ▼
                                    Redis Streams
                                          │
                               SlackMessageListener
                                   │ lock thread
                                   │ read thread history
                                   ▼
                               "opencode_messages"
                                   │
                           OpenCodeMessageListener
                                   │ find/reuse thread session
                                   ▼
                               OCServerAgent ──HTTP/SSE──▶ opencode serve :12335 ──▶ LLM provider
                                   │                              │
                                   │                              └─ GitHub / Javadoc MCP
                                   ▼
                         "opencode_session_completed"
                                   │
                     OpenCodeSessionCompletedListener
                                   │ unlock thread
                                   │ post Slack reply + reaction
                                   ▼
                                 Slack
```

### Message flow

1. Slack delivers an event to the Ktor endpoint on port `8080`, which proxies it to the embedded Slack Bolt server on port `3000`.
2. `SlackAppMentionController` and `SlackChannelController` publish a message to the `slack_messages` Redis stream.
3. `SlackMessageListener` acquires the thread lock, reads the Slack thread, sets the assistant "thinking" status and publishes to `opencode_messages`.
4. `OpenCodeMessageListener` looks up the opencode session bound to the thread, runs the agent and publishes the result to `opencode_session_completed`.
5. `OpenCodeSessionCompletedListener` releases the lock and posts the formatted response back into the Slack thread.

### Reliability

`OCServerAgent` subscribes to the opencode SSE event bus and drives a single turn to completion. If no event arrives for a configurable period, the watchdog checks whether the session is still active and either reconciles the outcome from the session messages or marks the session as stalled and requests a server restart. Every turn is bounded by `opencode.maxAgentSessionDurationSeconds` and interrupted on timeout so the thread stays responsive.

## Requirements

- JDK 25
- Docker (Redis for local runs, Testcontainers for integration tests)
- The opencode CLI v2 (`opencode` on `PATH`, or set `opencode.opencodeBinary`)
- Redis 8
- A Slack app with a bot token, signing secret and event subscriptions

## Configuration

Configuration is loaded from `src/main/resources/application.yaml` and profile-specific files such as `application-local.yaml` and `application-docker.yaml`. Select profiles with the `APP_PROFILES` environment variable (comma-separated). Any property can be overridden with an uppercase, underscore-separated environment variable using relaxed binding, for example:

| Property                  | Environment variable       |
| ------------------------- | -------------------------- |
| `opencode.model`          | `OPENCODE_MODEL`           |
| `slack.server.enabled`    | `SLACK_SERVER_ENABLED`     |
| `opencode.configSync.enabled` | `OPENCODE_CONFIGSYNC_ENABLED` |
| `opencode.configSync.repoUrl` | `OPENCODE_CONFIGSYNC_REPOURL` |

Common environment variables:

| Variable                       | Description                                              |
| ------------------------------ | -------------------------------------------------------- |
| `APP_PROFILES`                 | Active configuration profiles, e.g. `local` or `docker`  |
| `SLACK_BOT_TOKEN`              | Slack bot user OAuth token                               |
| `SLACK_APP_TOKEN`              | Slack app-level token                                    |
| `SLACK_SIGNING_SECRET`         | Slack request signing secret                             |
| `GITHUB_PERSONAL_ACCESS_TOKEN` | Token exposed to the opencode GitHub MCP server          |
| `OPENCODE_API_KEY`             | Provider API key passed to the managed opencode server   |

### Key settings

All settings live in `OpenCodeProps`, `SlackProps`, `SlackServerProps`, `OpenCodeServerProps`, `OpenCodeWatchdogProps` and `ConfigSyncProps`.

| Setting | Default | Description |
| ------- | ------- | ----------- |
| `opencode.model` | `opencode/big-pickle` | Model in `<provider>/<model>` form |
| `opencode.agent` | `slack-bot` | Agent name loaded from the opencode config |
| `opencode.maxAgentSessionDurationSeconds` | `180` | Hard limit for one turn |
| `opencode.threadSessionTtlSeconds` | `86400` | How long an idle thread keeps its opencode session |
| `opencode.server.port` | `12335` | Port of the managed `opencode serve` process |
| `slack.server.port` | `3000` | Embedded Slack Bolt server port |
| `slack.channels` | `[]` | Channel IDs whose top-level messages are handled |

## Local development

1. Create the Slack app:

   - Enable **Event Subscriptions** and point the Request URL at `https://<your-host>/slack/events`.
   - Subscribe to `app_mention` and `message.channels` (add `message.groups` for private channels).
   - Add bot scopes for reading history, posting messages, adding reactions and setting assistant status.
   - Add the `/oc` slash command.
   - Install the app to your workspace.

2. Create a `.env` file in the project root:

   ```dotenv
   SLACK_BOT_TOKEN=xoxb-...
   SLACK_APP_TOKEN=xapp-...
   SLACK_SIGNING_SECRET=...
   GITHUB_PERSONAL_ACCESS_TOKEN=...
   OPENCODE_API_KEY=...
   ```

3. Start Redis:

   ```bash
   docker run --rm -d -p 6379:6379 --name opencode-agents-redis redis:8-alpine
   ```

4. Run the application with the `local` profile:

   ```bash
   APP_PROFILES=local ./gradlew run
   ```

5. Invite the bot to a channel, then mention it or send a top-level message in a configured channel. The bot reacts with `:opencode:` while working and posts its answer as a thread reply with a `:checkered_flag:` reaction.

## Docker

The image packages the application and the opencode CLI. `docker-compose.yaml` starts the server, Redis and RedisInsight.

```bash
./gradlew build
docker compose up --build
```

The compose setup exposes the application on `http://localhost:8080`, sets `APP_PROFILES=docker` and loads secrets from `.env`. It also mounts:

- `./secrets/ssh-key` as the config-sync SSH key
- `./opencode-data/.ssh` as the opencode SSH directory
- `./opencode-config` as the opencode configuration directory

To sync the opencode configuration from Git at startup, set:

```yaml
environment:
  OPENCODE_CONFIGSYNC_ENABLED: "true"
  OPENCODE_CONFIGSYNC_REPOURL: "git@github.com:org/opencode-config.git"
  OPENCODE_CONFIGSYNC_SSH_PRIVATEKEYPATH: "/run/secrets/ssh-key"
```

The repository is cloned into `opencode.configSync.targetDir` only when that directory does not already exist.

## Agent configuration

The bot's behaviour is defined by the opencode configuration in `opencode-config/`:

- `opencode-config/opencode.json` enables the GitHub and Javadoc MCP servers and restricts filesystem, shell, edit, glob, grep and task permissions.
- `opencode-config/agents/slack-bot.md` defines the `slack-bot` agent: a fast triage assistant limited to 10 files per request, with GitHub/Javadoc MCP tools and a short, structured response format.

When running through Docker, mount or sync your own configuration directory and point `OPENCODE_CONFIG_DIR` / `OPENCODE_CONFIG` at it.

## Development

```bash
./gradlew build   # compile, test and assemble
./gradlew test    # run the test suite
```

The test suite uses:

- JUnit 5, AssertJ and Ktor `testApplication`
- Testcontainers for Redis and an SSH Git server
- WireMock to mock the opencode HTTP/SSE API

Integration tests require a running Docker daemon.

### Project layout

```
src/main/kotlin/io/github/torvehammok/
├── controllers/        Slack event and HTTP controllers
├── domain/             Agent orchestration, pricing and configuration models
├── infra/
│   ├── git/            SSH Git configuration sync
│   ├── opencode/       opencode client, server manager, listeners and watchdog
│   └── slack/          Slack Bolt app, server and Redis listeners
└── libs/               JSON and Redis helpers
opencode-config/        Agent prompt and opencode permissions/MCP setup
.github/workflows/      CI/CD and GitHub /oc automation
```

## CI/CD

`.github/workflows/cicd.yaml` builds and tests every push and pull request to `main`, then builds a multi-arch Docker image. Images are pushed to GHCR as `ghcr.io/<repository>:<main|feature>-<short-sha>`; pushes happen only from `main`.

`.github/workflows/opencode.yaml` lets repository owners, members and collaborators comment `/oc` or `/opencode` on an issue to run opencode against the repository.

## License

No license file is included in this repository.
