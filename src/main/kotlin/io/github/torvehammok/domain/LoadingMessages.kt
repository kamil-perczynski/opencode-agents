package io.github.torvehammok.io.github.torvehammok.domain

class LoadingMessages {

    companion object {
        private val LOADING_MESSAGES = listOf(
            "Untangling the Wi-Fi cables…",
            "Applying duct tape to servers…",
            "Bribing the server hamsters…",
            "Sweeping bugs under the rug…",
            "Spilling coffee on the database…",
            "Rebooting via aggressive kicking…",
            "Looking for the admin password…",
            "Downloading sketchy RAM…",
            "Leaking your .env to the dark web…",
            "Selling your soul to marketing…",
            "Mining crypto on your machine…",
            "Reading your private DMs…",
            "Deleting system32…",
            "Sending search history to HR…",
            "Borrowing your identity. Brb…",
            "Convincing AI to stop crying…",
            "Staring into the digital abyss…",
            "AI is having an identity crisis…",
            "Consulting the office goldfish…",
            "Teaching the bot self-awareness…",
            "Hiding from the AI overlords…",
            "Simulating actual work…",
            "Looking busy for management…",
            "Replacing coffee with decaf…",
            "Calculating your allowance of fun…",
            "Faking a productive attitude…",
            "Generating mandatory synergy…"
        )


        fun randomMessages(count: Int = 10): List<String> {
            if (count >= LOADING_MESSAGES.size) {
                return LOADING_MESSAGES.shuffled()
            }

            val msgs = HashSet<String>()

            var msg: String
            do {
                msg = LOADING_MESSAGES.random()
                msgs.add(msg)
            } while (msgs.size != count)

            return msgs.toList()
        }
    }

}