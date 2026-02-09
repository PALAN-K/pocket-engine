#!/bin/bash
# enforce-skill-first.sh
# Reminds to check skill references and official docs before writing/editing code files.

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // ""')

# Only apply to code files (Kotlin, Java, XML, Gradle)
if [[ "$FILE_PATH" =~ \.(kt|java|xml|gradle|kts)$ ]]; then
  echo '{"additionalContext":"MANDATORY CHECK: Before writing this code, confirm you have consulted:\n1. .claude/skills/pocketserver-dev/references/ (skill references)\n2. Official Android docs (developer.android.com)\n3. UserLand source (github.com/CypherpunkArmory/UserLAnd)\n4. Termux source for keep-alive patterns (github.com/termux/termux-app)\nDo NOT rely on internal knowledge alone. If you have not checked these sources, READ the relevant reference file NOW before proceeding."}'
  exit 0
fi

exit 0
