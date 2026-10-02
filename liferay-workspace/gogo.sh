#!/usr/bin/env sh
# Spustí Gogo příkaz uvnitř kontejneru přes telnet, např.:
#   ./gogo.sh lb com.example
#   ./gogo.sh "diag 1234"
#   ./gogo.sh "services com.example.greeting.api.GreetingService"
#   ./gogo.sh "greeting:hello Erich"
cd "$(dirname "$0")"
export MSYS_NO_PATHCONV=1  # Git Bash na Windows jinak přepisuje cesty
docker compose exec -T liferay sh -c "(echo '$*'; sleep 2; echo 'disconnect'; sleep 1; echo y) | telnet localhost 11311 2>/dev/null" | sed -n '/^g!/,$p' | grep -v -e '^Connection closed' -e 'disconnect' -e 'Disconnect from console'
