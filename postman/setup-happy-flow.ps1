param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$OutputPath = (Join-Path $PSScriptRoot 'HappyFlow.local.postman_environment.json')
)

$ErrorActionPreference = 'Stop'
# Local development only: HTTP registration hashes the password; SQL provisions
# the role and locations because this API has no endpoints for those operations.
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskEmail = 'happy.owner.' + [guid]::NewGuid().ToString('N') + '@example.com'
$taskPassword = 'Happy!' + [guid]::NewGuid().ToString('N')
$taskCredentials = @{ email = $taskEmail; password = $taskPassword }
$taskRegistration = @{
    name = 'Happy Flow Owner'; email = $taskEmail; password = $taskPassword
    phone = '+31612345678'; address = 'Teststraat 1, Amsterdam'
} | ConvertTo-Json
Invoke-RestMethod -Uri ($BaseUrl.TrimEnd('/') + '/register') -Method Post -ContentType 'application/json' -Body $taskRegistration | Out-Null
$taskLogin = Invoke-RestMethod -Uri ($BaseUrl.TrimEnd('/') + '/login') -Method Post -ContentType 'application/json' -Body ($taskCredentials | ConvertTo-Json)
$taskPayload = $taskLogin.token.Split('.')[1].Replace('-', '+').Replace('_', '/')
$taskPayload = $taskPayload.PadRight($taskPayload.Length + ((4 - $taskPayload.Length % 4) % 4), '=')
$taskOwnerId = [long](([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($taskPayload)) | ConvertFrom-Json).userId)
if ($taskOwnerId -le 0) { throw 'Registration/login did not return a valid user ID.' }

# Only a newly generated fixture account is promoted. User input is not inserted
# into SQL; the ID is parsed as a positive integer from its authenticated login.
$taskSql = @"
START TRANSACTION;
UPDATE users SET role = 'OWNER' WHERE id = $taskOwnerId;
INSERT INTO locations (streetName, houseNumber, postalCode, city, country, userId)
VALUES ('Happy Pickupstraat', '1', '1234AB', 'Amsterdam', 'Netherlands', $taskOwnerId);
SET @happy_pickup = LAST_INSERT_ID();
INSERT INTO locations (streetName, houseNumber, postalCode, city, country, userId)
VALUES ('Happy Dropoffstraat', '2', '1234CD', 'Amsterdam', 'Netherlands', $taskOwnerId);
SELECT @happy_pickup, LAST_INSERT_ID();
COMMIT;
"@
Push-Location -LiteralPath $taskRoot
try {
    $taskResult = $taskSql | docker compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" -D"$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) { throw 'Owner/location provisioning failed; no environment file was written.' }
} finally {
    Pop-Location
}
$taskLocationIds = @([regex]::Matches(($taskResult -join "`n"), '\d+') | ForEach-Object { $_.Value })
if ($taskLocationIds.Count -ne 2) { throw 'Could not read the two new location IDs.' }

# Log in again: the token issued before promotion still has the RENTER claim.
$taskOwnerLogin = Invoke-RestMethod -Uri ($BaseUrl.TrimEnd('/') + '/login') -Method Post -ContentType 'application/json' -Body ($taskCredentials | ConvertTo-Json)
if ($taskOwnerLogin.role -ne 'OWNER') { throw 'The owner role was not applied to the API database. Check BaseUrl and Docker configuration.' }
$taskValues = [ordered]@{
    baseUrl = $BaseUrl.TrimEnd('/'); ownerEmail = $taskEmail; ownerPassword = $taskPassword
    pickupId = $taskLocationIds[0]; dropOffId = $taskLocationIds[1]
}
$taskEnvironment = @{
    id = [guid]::NewGuid().ToString(); name = 'RentMyCar Happy Flow - Local'
    values = @($taskValues.GetEnumerator() | ForEach-Object {
        @{ key = $_.Key; value = [string]$_.Value; enabled = $true; type = $(if ($_.Key -eq 'ownerPassword') { 'secret' } else { 'default' }) }
    })
    _postman_variable_scope = 'environment'
}
$taskEnvironment | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding utf8
Write-Output "Created a new local OWNER and two locations. Import this environment into Postman: $OutputPath"
Write-Output 'Then run RentMyCar-Happy-Flows.postman_collection.json in order (one iteration).'
