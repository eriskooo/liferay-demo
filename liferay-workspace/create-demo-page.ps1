# Vytvorí verejnú widget stránku s Task portletom cez JSON WS (Basic Auth), PowerShell verzia create-demo-page.sh.
# Použitie: .\create-demo-page.ps1 [friendlyUrl]   (predvolene /task-demo)
#
# Pozn.: jednoduchšia varianta add-layout (name/title/friendlyURL) v GA132 opakovane
# padala na LayoutFriendlyURLException -> používame variantu s mapami + typeSettings.
param([string]$Url = '/task-demo')

$base = 'http://localhost:8080/api/jsonws'
$token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('test@liferay.com:test'))
$headers = @{ Authorization = "Basic $token" }

$company = Invoke-RestMethod -Headers $headers "$base/company/get-company-by-web-id?webId=liferay.com"
$group = Invoke-RestMethod -Headers $headers "$base/group/get-group?companyId=$($company.companyId)&groupKey=Guest"
"Guest groupId=$($group.groupId)"

# typeSettings widget stránky = layout šablóna + portlety v stĺpcoch
$body = @{
	externalReferenceCode = 'page' + $Url.Replace('/', '-')
	groupId = $group.groupId
	privateLayout = 'false'
	parentLayoutId = 0
	localeNamesMap = '{"en_US":"Tasks"}'
	localeTitlesMap = '{}'
	descriptionMap = '{}'
	keywordsMap = '{}'
	robotsMap = '{}'
	type = 'portlet'
	typeSettings = "layout-template-id=1_column`ncolumn-1=com_example_task_web_TaskPortlet`n"
	hidden = 'false'
	friendlyURLMap = '{"en_US":"' + $Url + '"}'
	serviceContext = '{}'
}

try {
	$layout = Invoke-RestMethod -Method Post -Headers $headers "$base/layout/add-layout" -Body $body
} catch {
	$layout = $null
	Write-Warning $_.Exception.Message
}

if (-not $layout.layoutId) {
	Write-Error 'Stránku sa nepodarilo vytvoriť (už existuje? pozri docker compose logs liferay)'
	exit 1
}

"Hotovo: http://localhost:8080/web/guest$Url"
