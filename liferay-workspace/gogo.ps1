# Spustí Gogo príkaz vnútri kontajnera cez telnet (PowerShell verzia gogo.sh), napr.:
#   .\gogo.ps1 lb com.example
#   .\gogo.ps1 "diag 1234"
#   .\gogo.ps1 "services com.example.greeting.api.GreetingService"
#   .\gogo.ps1 "greeting:hello Erich"
$command = $args -join ' '
$telnet = "(echo '$command'; sleep 2; echo 'disconnect'; sleep 1; echo y) | telnet localhost 11311 2>/dev/null"

# Výstup telnetu začína bannerom; zaujíma nás až všetko od prvého promptu g!
$started = $false
docker compose -f "$PSScriptRoot\docker-compose.yml" exec -T liferay sh -c $telnet |
	ForEach-Object { if ($_ -match '^g!') { $started = $true }; if ($started) { $_ } } |
	Where-Object { $_ -notmatch '^Connection closed|disconnect|Disconnect from console' }
