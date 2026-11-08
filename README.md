# opencode-agents

A Slack bot backend that puts an [OpenCode](https://opencode.ai) coding agent into your Slack threads.

The service supervises a local `opencode serve` process, forwards Slack messages and mentions to OpenCode
sessions, streams session events, and posts the agent's answer (with duration, tool count and estimated cost)
back to the thread. A Slack thread keeps a single OpenCode session, so follow-up messages continue the same
conversation.

## Features

- Slack integration through Socket Mode and a forwarded Slack events endpoint
- Thread-aware, resumable OpenCode sessions
- Redis Streams pipeline that decouples Slack ingestion from agent execution
- Redis locks so two messages in the same thread never run the agent concurrently
- Watchdog that detects stalled sessions, reconnects the event stream and restarts the OpenCode server with backoff
- Cost estimation from model list prices when the provider reports no cost
- Optional OpenCode configuration sync from a Git repository over SSH
- Health/readiness checks, metrics and structured logging
- Multi-arch Docker image bundling the OpenCode CLI

## Architecture

```
Slack
  |  events / mentions
  v
Slack controllers  --publish-->  redis stream: slack_messages
                                        |
                                        v
                                SlackMessageListener
                          (reads thread, locks it, publishes)
                                        |
                                        v
                                redis stream: opencode_messages
                                        |
                                        v
                               OpenCodeMessageListener
                                        |
                                        v
                                  OCServerAgent  <-->  opencode serve
                                        |
                                        v
                        redis stream: opencode_session_completed
                                        |
                                        v
                          OpenCodeSessionCompletedListener
                                        |
                                        v
                                   Slack thread
```

The OpenCode server is spawned and supervised by `OpenCodeServerManager`. The agent talks to it over its HTTP
API and subscribes to the session event stream. The Slack thread lock is held until the completion message has
been handled and the response posted.

## Requirements

- JDK 25
- Redis 8 (or compatible)
- The OpenCode CLI available on `PATH` (bundled in the Docker image)
- A Slack app with the required scopes and event subscriptions (see below)

## Configuration

Configuration is loaded from `src/main/resources/application.yaml` plus an optional profile file
(`application-local.yaml`, `application-docker.yaml`). The active profiles are selected with `APP_PROFILES`
(for example `APP_PROFILES=local`). Environment variables are loaded from `.env` when present.

The most important settings:

| Property | Env variable | Description |
| --- | --- | --- |
| `slack.botToken` | `SLACK_BOT_TOKEN` | Slack bot user OAuth token |
| `slack.appToken` | `SLACK_APP_TOKEN` | Slack app-level token for Socket Mode |
| `slack.signingSecret` | `SLACK_SIGNING_SECRET` | Slack request signing secret |
| `slack.channels` | - | Channels the bot listens on (non-thread messages) |
| `slack.server.enabled` | - | Enable the internal Slack events server |
| `opencode.model` | - | Model used for agent sessions |
| `opencode.agent` | - | Agent name from the OpenCode configuration |
| `opencode.password` | - | Password passed to `opencode serve` |
| `opencode.server.env.custom` | - | Extra environment variables for the OpenCode process |
| `opencode.configSync.*` | `OPENCODE_CONFIGSYNC_*` | Optional Git-based OpenCode config sync |
| `redis.url` | - | Redis connection URL |

See the property classes under `src/main/kotlin/io/github/torvehammok/domain` for the full list, including
watchdog timeouts, session TTLs and Git/SSH settings.

### `.env` example

```dotenv
SLACK_BOT_TOKEN=xoxb-...
SLACK_APP_TOKEN=xapp-...
SLACK_SIGNING_SECRET=...
GITHUB_PERSONAL_ACCESS_TOKEN=ghp_...
OPENCODE_API_KEY=...
```

## Running locally

Start Redis, make sure `opencode` is installed, then run the application with the local profile:

```bash
docker compose up -d redis
APP_PROFILES=local ./gradlew run
```

The Ktor server listens on port `8080`. With the `local` profile, Slack is enabled and reads tokens from `.env`.

## Running with Docker

```bash
# create a .env with the variables above
docker compose up --build
```

The compose file starts the application, Redis and RedisInsight. It mounts an SSH key and an OpenCode config
directory and enables configuration sync via the `OPENCODE_CONFIGSYNC_*` variables. Adjust the repository URL,
mount paths and secrets before using it.

## Slack app setup

Create a Slack app and enable:

- **Socket Mode** and an app-level token (`connections:write`)
- **Event subscriptions**: `app_mention` and `message.channels`
- **Slash command** `/oc`
- **Bot scopes**: `app_mentions:read`, `channels:history`, `chat:write`, `reactions:write`, `commands`,
  and the assistant scopes used for thread status (`assistant:write`)

Set the bot token, app token and signing secret in the environment. Add the channel IDs you want the bot to
listen on to `slack.channels`.

## OpenCode configuration

`opencode-config/opencode.json` configures the agents and MCP servers used by the OpenCode process:

- `github` MCP server, authenticated with `GITHUB_PERSONAL_ACCESS_TOKEN`
- `javadocs` MCP server
- the `slack-bot` agent, with filesystem access disabled and the tool permissions used by this service

`opencode-config/agents/slack-bot.md` contains the agent instructions. For production, point
`OPENCODE_CONFIG_DIR` and `OPENCODE_CONFIG` at a directory synced from your own configuration repository
using `opencode.configSync`.

## Development

```bash
./gradlew build     # compile, run tests and build the distribution
./gradlew test      # tests only
```

Tests use JUnit 5, AssertJ, WireMock and Testcontainers. Some integration tests require Docker and Redis to be
available.

### Project layout

```
src/main/kotlin/io/github/torvehammok
├── controllers/        Slack event controllers
├── domain/             Agent, thread and configuration model
├── infra/
│   ├── git/            SSH Git config sync
│   ├── opencode/       OpenCode client, server manager and Redis listeners
│   └── slack/          Slack app, server and Redis listeners
└── KtorMain.kt         Application entry point
```

## CI/CD

`.github/workflows/cicd.yaml` builds and tests every push and pull request, publishes the JUnit report and
pushes a multi-arch image to GitHub Container Registry on `main`.

`.github/workflows/opencode.yaml` runs OpenCode on issue comments that start with `/oc` or `/opencode`, so
maintainers can ask the agent to implement changes directly from an issue.
