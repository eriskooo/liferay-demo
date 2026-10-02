#!/usr/bin/env sh
# Vytvoří veřejnou widget stránku s Task portletem přes JSON WS (Basic Auth).
# Použití: ./create-demo-page.sh [friendlyUrl]   (výchozí /task-demo)
#
# Pozn.: jednodušší varianta add-layout (name/title/friendlyURL) v GA132 opakovaně
# padala na LayoutFriendlyURLException -> používáme variantu s mapami + typeSettings.
URL=${1:-/task-demo}
AUTH=test@liferay.com:test
BASE=http://localhost:8080/api/jsonws

COMPANY_ID=$(curl -s -u $AUTH "$BASE/company/get-company-by-web-id?webId=liferay.com" | grep -o '"companyId":"*[0-9]*' | grep -o '[0-9]*$')
GROUP_ID=$(curl -s -u $AUTH "$BASE/group/get-group?companyId=$COMPANY_ID&groupKey=Guest" | grep -o '"groupId":"*[0-9]*' | grep -o '[0-9]*$')
echo "Guest groupId=$GROUP_ID"

# typeSettings widget stránky = layout šablona + portlety ve sloupcích
LAYOUT_ID=$(curl -s -u $AUTH "$BASE/layout/add-layout" \
	-d externalReferenceCode="page$(echo "$URL" | tr '/' '-')" \
	-d groupId="$GROUP_ID" -d privateLayout=false -d parentLayoutId=0 \
	--data-urlencode 'localeNamesMap={"en_US":"Tasks"}' \
	-d 'localeTitlesMap={}' -d 'descriptionMap={}' -d 'keywordsMap={}' -d 'robotsMap={}' \
	-d type=portlet \
	--data-urlencode "typeSettings=layout-template-id=1_column
column-1=com_example_task_web_TaskPortlet
" \
	-d hidden=false \
	--data-urlencode "friendlyURLMap={\"en_US\":\"$URL\"}" \
	-d serviceContext='{}' | grep -o '"layoutId":"*[0-9]*' | grep -o '[0-9]*$')

if [ -z "$LAYOUT_ID" ]; then
	echo "Stránku se nepodařilo vytvořit (už existuje? viz docker compose logs liferay)"
	exit 1
fi

echo "Hotovo: http://localhost:8080/web/guest$URL"
