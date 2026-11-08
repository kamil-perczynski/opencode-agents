# `opencode github run` logs through Effect's default logger, ignoring OPENCODE_LOG_LEVEL / OPENCODE_PRINT_LOGS.
# Drop its INFO/DEBUG entries (header line + multi-line object) and shorten huge tool-call lines; keep everything else.
/^\[[0-9:.]+\] (INFO|DEBUG|TRACE) \(#[0-9]+\):/ { skip = ($0 ~ /\{$/); next }
skip { if ($0 ~ /^\}/) skip = 0; next }
length($0) > 300 { $0 = substr($0, 1, 300) "…" }
{ print; fflush() }
