#!/bin/bash
# enforce-sequential-tasks.sh
# BLOCKS parallel Task tool calls. All subagents must run sequentially.

INPUT=$(cat)

# Check if this is being called while another task is potentially running
# The key enforcement: if the prompt mentions parallel/concurrent patterns, deny
PROMPT=$(echo "$INPUT" | jq -r '.tool_input.prompt // ""')
DESC=$(echo "$INPUT" | jq -r '.tool_input.description // ""')

# Block if prompt explicitly requests parallel execution
if echo "$PROMPT $DESC" | grep -qi "parallel\|simultaneously\|concurrent\|at the same time\|in parallel"; then
  echo '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"BLOCKED: Parallel subagent execution is forbidden. Run tasks SEQUENTIALLY (one at a time). Wait for each task to complete before starting the next."}}'
  exit 0
fi

# Allow the task to proceed
exit 0
