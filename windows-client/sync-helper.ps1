param(
    [string]$ConfigPath,
    [string]$CommandPath,
    [string]$EventPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.Windows.Forms

function Get-IniValue {
    param(
        [string]$Path,
        [string]$Section,
        [string]$Key,
        [string]$Default = ''
    )
    if (-not (Test-Path $Path)) { return $Default }
    $currentSection = ''
    foreach ($line in Get-Content $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed -match '^\[(.+)\]$') {
            $currentSection = $matches[1]
            continue
        }
        if ($currentSection -eq $Section -and $trimmed -match "^(?<key>[^=]+?)=(?<value>.*)$") {
            if ($matches['key'].Trim() -eq $Key) {
                return $matches['value'].Trim()
            }
        }
    }
    return $Default
}

function Write-EventLine {
    param([string]$Line)
    Add-Content -Path $EventPath -Value $Line -Encoding UTF8
}

function Write-Status {
    param([string]$Status)
    Write-EventLine "status|$Status"
}

function Write-Log {
    param([string]$Message)
    $bytes = [Text.Encoding]::UTF8.GetBytes($Message)
    $encoded = [Convert]::ToBase64String($bytes)
    Write-EventLine "log|$encoded"
}

function Write-Clipboard {
    param([string]$Text)
    $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
    $encoded = [Convert]::ToBase64String($bytes)
    Write-EventLine "clipboard|$encoded"
}

function Write-JsonEvent {
    param(
        [string]$Type,
        [object]$Value
    )
    $json = $Value | ConvertTo-Json -Depth 8 -Compress
    $bytes = [Text.Encoding]::UTF8.GetBytes($json)
    $encoded = [Convert]::ToBase64String($bytes)
    Write-EventLine "$Type|$encoded"
}

function Get-MimeType {
    param([string]$Path)
    switch -Regex ([IO.Path]::GetExtension($Path).ToLowerInvariant()) {
        '^\.(png)$' { return 'image/png' }
        '^\.(jpe?g)$' { return 'image/jpeg' }
        '^\.(gif)$' { return 'image/gif' }
        '^\.(bmp)$' { return 'image/bmp' }
        '^\.(webp)$' { return 'image/webp' }
        '^\.(svg)$' { return 'image/svg+xml' }
        '^\.(avif)$' { return 'image/avif' }
        '^\.(heic|heif)$' { return 'image/heic' }
        default { return 'application/octet-stream' }
    }
}

function Get-PayloadKind {
    param([string]$Path)
    if ((Get-MimeType -Path $Path).StartsWith('image/')) {
        return 'image'
    }
    return 'file'
}

function Get-OptionalProperty {
    param(
        [object]$Object,
        [string]$Name,
        $Default = $null
    )
    if ($null -eq $Object) {
        return $Default
    }
    if ($Object.PSObject.Properties.Name -contains $Name) {
        return $Object.$Name
    }
    return $Default
}

function Get-Config {
    @{
        ServerBase = (Get-IniValue -Path $ConfigPath -Section 'sync' -Key 'serverBase' -Default 'http://127.0.0.1:9501').TrimEnd('/')
        Room = Get-IniValue -Path $ConfigPath -Section 'sync' -Key 'room' -Default ''
        RoomPassword = Get-IniValue -Path $ConfigPath -Section 'sync' -Key 'roomPassword' -Default ''
        DeviceName = Get-IniValue -Path $ConfigPath -Section 'sync' -Key 'deviceName' -Default 'Windows 同步端'
        DeviceId = Get-IniValue -Path $ConfigPath -Section 'sync' -Key 'deviceId' -Default ([guid]::NewGuid().ToString())
    }
}

function Get-TrustStatusText {
    param(
        [bool]$Trusted,
        [bool]$Paused = $false
    )
    if ($Paused) { return '已暂停同步' }
    if ($Trusted) { return '已信任' }
    return '等待批准'
}

function Get-AuthHeaders {
    param([hashtable]$Config)
    $headers = @{}
    if ($Config.RoomPassword) {
        $headers['Authorization'] = "Bearer $($Config.RoomPassword)"
    }
    return $headers
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Uri,
        [object]$Body = $null,
        [hashtable]$Config
    )
    $params = @{
        Method = $Method
        Uri = $Uri
        Headers = (Get-AuthHeaders -Config $Config)
    }
    if ($Body -ne $null) {
        $params['Body'] = ($Body | ConvertTo-Json -Depth 5)
        $params['ContentType'] = 'application/json'
    }
    Invoke-RestMethod @params
}

function New-HttpClient {
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $client = [System.Net.Http.HttpClient]::new($handler)
    return $client
}

function Send-HttpRequest {
    param(
        [System.Net.Http.HttpClient]$Client,
        [System.Net.Http.HttpMethod]$Method,
        [string]$Uri,
        [System.Net.Http.HttpContent]$Content = $null,
        [hashtable]$Config,
        [string]$Accept = 'application/json'
    )
    $request = [System.Net.Http.HttpRequestMessage]::new($Method, $Uri)
    if ($Content -ne $null) {
        $request.Content = $Content
    }
    if ($Config.RoomPassword) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Config.RoomPassword)
    }
    if ($Accept) {
        $request.Headers.Accept.Add([System.Net.Http.Headers.MediaTypeWithQualityHeaderValue]::new($Accept))
    }
    return $Client.SendAsync($request).GetAwaiter().GetResult()
}

function Upload-FileToServer {
    param(
        [System.Net.Http.HttpClient]$HttpClient,
        [hashtable]$Config,
        [string]$FilePath
    )
    $fileItem = Get-Item -LiteralPath $FilePath
    $initUri = "$($Config.ServerBase)/upload/chunk"
    if ($Config.Room) {
        $initUri += "?room=$([uri]::EscapeDataString($Config.Room))"
    }
    $nameContent = [System.Net.Http.StringContent]::new($fileItem.Name, [Text.Encoding]::UTF8, 'text/plain')
    $nameContent.Headers.ContentType.CharSet = ''
    $initResponse = Send-HttpRequest -Client $HttpClient -Method ([System.Net.Http.HttpMethod]::Post) -Uri $initUri -Content $nameContent -Config $Config
    try {
        $initBody = $initResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $initResponse.IsSuccessStatusCode) {
            throw "初始化上传失败：HTTP $([int]$initResponse.StatusCode)"
        }
        $initJson = $initBody | ConvertFrom-Json
        $uuid = $initJson.result.uuid
        if (-not $uuid) {
            throw '初始化上传失败：未返回 uuid'
        }
    } finally {
        $initResponse.Dispose()
        $nameContent.Dispose()
    }

    $chunkSize = 4MB
    $stream = [System.IO.File]::OpenRead($fileItem.FullName)
    try {
        while ($stream.Position -lt $stream.Length) {
            $remaining = [int][Math]::Min([int64]$chunkSize, $stream.Length - $stream.Position)
            $buffer = [byte[]]::new($remaining)
            $read = $stream.Read($buffer, 0, $remaining)
            if ($read -le 0) { break }
            $chunkUri = "$($Config.ServerBase)/upload/chunk/$uuid"
            $chunkContent = [System.Net.Http.ByteArrayContent]::new($buffer, 0, $read)
            $chunkContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new('application/octet-stream')
            $chunkResponse = Send-HttpRequest -Client $HttpClient -Method ([System.Net.Http.HttpMethod]::Post) -Uri $chunkUri -Content $chunkContent -Config $Config
            try {
                if (-not $chunkResponse.IsSuccessStatusCode) {
                    throw "上传分块失败：HTTP $([int]$chunkResponse.StatusCode)"
                }
            } finally {
                $chunkResponse.Dispose()
                $chunkContent.Dispose()
            }
        }
    } finally {
        $stream.Dispose()
    }

    $finishUri = "$($Config.ServerBase)/upload/finish/$uuid"
    if ($Config.Room) {
        $finishUri += "?room=$([uri]::EscapeDataString($Config.Room))"
    }
    $finishResponse = Send-HttpRequest -Client $HttpClient -Method ([System.Net.Http.HttpMethod]::Post) -Uri $finishUri -Config $Config
    try {
        $finishBody = $finishResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $finishResponse.IsSuccessStatusCode) {
            throw "完成上传失败：HTTP $([int]$finishResponse.StatusCode)"
        }
        $finishJson = $finishBody | ConvertFrom-Json
        if ($finishJson.PSObject.Properties.Name -contains 'result') {
            return $finishJson.result
        }
        return $finishJson
    } finally {
        $finishResponse.Dispose()
    }
}

function Publish-PayloadNotice {
    param(
        [hashtable]$Config,
        [object]$Notice
    )
    Invoke-Json -Method POST -Uri "$($Config.ServerBase)/api/sync/payload-notice" -Body $Notice -Config $Config | Out-Null
}

function Send-PayloadFiles {
    param(
        [System.Net.Http.HttpClient]$HttpClient,
        [hashtable]$Config,
        [string[]]$Paths
    )
    foreach ($rawPath in $Paths) {
        $filePath = $rawPath.Trim()
        if (-not $filePath) { continue }
        if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
            Write-Log "文件不存在，已跳过：$filePath"
            continue
        }
        $uploadResult = Upload-FileToServer -HttpClient $HttpClient -Config $Config -FilePath $filePath
        $fileItem = Get-Item -LiteralPath $filePath
        $uploadKind = Get-OptionalProperty -Object $uploadResult -Name 'kind'
        $uploadName = Get-OptionalProperty -Object $uploadResult -Name 'name'
        $uploadSize = Get-OptionalProperty -Object $uploadResult -Name 'size'
        $uploadActionUrl = Get-OptionalProperty -Object $uploadResult -Name 'actionUrl'
        $uploadUrl = Get-OptionalProperty -Object $uploadResult -Name 'url'
        $uploadDownloadUrl = Get-OptionalProperty -Object $uploadResult -Name 'downloadUrl'
        $notice = @{
            sourceDeviceId = $Config.DeviceId
            room = $Config.Room
            kind = $(if ($uploadKind) { $uploadKind } else { Get-PayloadKind -Path $filePath })
            title = $(if ($uploadName) { $uploadName } else { $fileItem.Name })
            mime = Get-MimeType -Path $filePath
            size = $(if ($uploadSize) { [int64]$uploadSize } else { [int64]$fileItem.Length })
            actionUrl = $(if ($uploadActionUrl) { $uploadActionUrl } elseif ($uploadUrl) { $uploadUrl } else { $null })
            downloadUrl = $uploadDownloadUrl
            createdAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        }
        Publish-PayloadNotice -Config $Config -Notice $notice
        Write-Log "已通知安卓接收：$($fileItem.Name)"
    }
}

function Resolve-ServerUrl {
    param(
        [hashtable]$Config,
        [string]$Url
    )
    if (-not $Url) {
        return $null
    }
    if ($Url -match '^https?://') {
        return $Url
    }
    if ($Url.StartsWith('/')) {
        return "$($Config.ServerBase)$Url"
    }
    return "$($Config.ServerBase)/$Url"
}

function Get-DownloadDirectory {
    param([string]$EventLogPath)
    $runtimeDir = Split-Path -Parent $EventLogPath
    return Join-Path $runtimeDir 'received-payloads'
}

function Get-SafePayloadFileName {
    param(
        [string]$PayloadId,
        [string]$Title
    )
    $safeName = ($Title -replace '[\\/:*?"<>|]', '_').Trim()
    if (-not $safeName) {
        $safeName = $PayloadId
    }
    return "${PayloadId}_$safeName"
}

function Set-ClipboardFiles {
    param([string[]]$Paths)
    $collection = New-Object System.Collections.Specialized.StringCollection
    foreach ($path in $Paths) {
        if ($path) {
            [void]$collection.Add($path)
        }
    }
    [System.Windows.Forms.Clipboard]::SetFileDropList($collection)
}

function Receive-PayloadFile {
    param(
        [System.Net.Http.HttpClient]$HttpClient,
        [hashtable]$Config,
        [hashtable]$ReceivedPayloads,
        [string]$PayloadId,
        [string]$Mode,
        [bool]$PasteAfterCopy,
        [string]$EventLogPath
    )
    if (-not $ReceivedPayloads.ContainsKey($PayloadId)) {
        throw "未找到待接收内容：$PayloadId"
    }
    $notice = $ReceivedPayloads[$PayloadId]
    $downloadUrl = Resolve-ServerUrl -Config $Config -Url $(if ($notice.downloadUrl) { $notice.downloadUrl } else { $notice.actionUrl })
    if (-not $downloadUrl) {
        throw '当前内容没有可下载地址'
    }
    $downloadDir = Get-DownloadDirectory -EventLogPath $EventLogPath
    New-Item -ItemType Directory -Path $downloadDir -Force | Out-Null
    $filePath = Join-Path $downloadDir (Get-SafePayloadFileName -PayloadId $PayloadId -Title $notice.title)
    $response = Send-HttpRequest -Client $HttpClient -Method ([System.Net.Http.HttpMethod]::Get) -Uri $downloadUrl -Config $Config -Accept '*/*'
    try {
        if (-not $response.IsSuccessStatusCode) {
            throw "下载失败：HTTP $([int]$response.StatusCode)"
        }
        $stream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
        try {
            $output = [System.IO.File]::Open($filePath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::Read)
            try {
                $stream.CopyTo($output)
            } finally {
                $output.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
    } finally {
        $response.Dispose()
    }

    $result = [ordered]@{
        payloadId = $PayloadId
        title = $notice.title
        path = $filePath
        mode = $Mode
        paste = $PasteAfterCopy
    }
    if ($Mode -eq 'clipboard' -or $Mode -eq 'clipboardPaste') {
        Set-ClipboardFiles -Paths @($filePath)
        Write-JsonEvent -Type 'payloadClipboardReady' -Value $result
    } else {
        Write-JsonEvent -Type 'payloadDownloaded' -Value $result
    }
}

[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

$Config = Get-Config
$httpClient = New-HttpClient
$wsUrl = $Config.ServerBase -replace '^http', 'ws'
$wsUrl = "$wsUrl/sync/ws?room=$([uri]::EscapeDataString($Config.Room))"
if ($Config.RoomPassword) {
    $wsUrl += "&auth=$([uri]::EscapeDataString($Config.RoomPassword))"
}

$client = [System.Net.WebSockets.ClientWebSocket]::new()
$cts = [Threading.CancellationTokenSource]::new()
$buffer = New-Object byte[] 8192
$commandLineCount = 0
$lastBootstrapAt = [datetime]::MinValue
$trusted = $false
$paused = $false
$receiveTask = $null
$receivedPayloads = @{}

try {
    Write-Status '连接中'
    $client.ConnectAsync([Uri]$wsUrl, $cts.Token).GetAwaiter().GetResult()
    Write-Status '已连接'

    $hello = @{
        event = 'hello'
        data = @{
            deviceId = $Config.DeviceId
            name = $Config.DeviceName
            room = $Config.Room
            platform = 'windows'
            clientType = 'autohotkey'
            meta = @{
                os = [Environment]::OSVersion.VersionString
            }
        }
    } | ConvertTo-Json -Depth 5
    $helloBytes = [Text.Encoding]::UTF8.GetBytes($hello)
    $client.SendAsync([ArraySegment[byte]]::new($helloBytes), [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $cts.Token).GetAwaiter().GetResult()

    while ($client.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
        if ((Get-Date) -gt $lastBootstrapAt.AddSeconds(8)) {
            try {
                $bootstrap = Invoke-Json -Method GET -Uri "$($Config.ServerBase)/api/sync/bootstrap?room=$([uri]::EscapeDataString($Config.Room))&deviceId=$([uri]::EscapeDataString($Config.DeviceId))" -Config $Config
                if ($bootstrap.device) {
                    $trusted = [bool]$bootstrap.device.trusted
                    Write-Status (Get-TrustStatusText -Trusted $trusted -Paused $paused)
                }
            } catch {
                Write-Log "刷新设备状态失败：$($_.Exception.Message)"
            }
            $lastBootstrapAt = Get-Date
        }

        if (Test-Path $CommandPath) {
            $lines = @(Get-Content $CommandPath -Encoding UTF8)
            while ($commandLineCount -lt $lines.Count) {
                $line = $lines[$commandLineCount]
                $commandLineCount++
                if (-not $line) { continue }
                $parts = $line.Split('|', 2)
                $command = $parts[0]
                if ($parts.Count -gt 1 -and $parts[1]) {
                    $payload = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($parts[1]))
                } else {
                    $payload = ''
                }
                switch ($command) {
                    'publish' {
                        if ($paused -or -not $trusted) { continue }
                        $message = @{
                            event = 'clipboardPublish'
                            data = @{
                                messageId = [guid]::NewGuid().ToString()
                                text = $payload
                                createdAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                            }
                        } | ConvertTo-Json -Depth 5
                        $bytes = [Text.Encoding]::UTF8.GetBytes($message)
                        $client.SendAsync([ArraySegment[byte]]::new($bytes), [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $cts.Token).GetAwaiter().GetResult()
                    }
                    'toggle' {
                        $paused = ($payload -eq 'off')
                        Write-Status (Get-TrustStatusText -Trusted $trusted -Paused $paused)
                    }
                    'payload' {
                        if ($paused) {
                            Write-Log '当前已暂停同步，未发送文件通知'
                            continue
                        }
                        if (-not $trusted) {
                            Write-Log '当前设备尚未获批，未发送文件通知'
                            continue
                        }
                        $paths = @($payload -split "`r?`n" | Where-Object { $_.Trim() })
                        if (-not $paths.Count) {
                            continue
                        }
                        try {
                            Send-PayloadFiles -HttpClient $httpClient -Config $Config -Paths $paths
                        } catch {
                            Write-Log "发送文件通知失败：$($_.Exception.Message)"
                        }
                    }
                    'payloadReceive' {
                        if ($paused) {
                            Write-Log '当前已暂停同步，未下载远端文件'
                            continue
                        }
                        if (-not $trusted) {
                            Write-Log '当前设备尚未获批，未下载远端文件'
                            continue
                        }
                        try {
                            $request = $payload | ConvertFrom-Json
                            Receive-PayloadFile -HttpClient $httpClient -Config $Config -ReceivedPayloads $receivedPayloads -PayloadId $request.payloadId -Mode $request.mode -PasteAfterCopy ([bool]$request.paste) -EventLogPath $EventPath
                            Write-Log "已接收远端文件：$($request.payloadId)"
                        } catch {
                            Write-Log "接收远端文件失败：$($_.Exception.Message)"
                        }
                    }
                    'shutdown' {
                        $cts.Cancel()
                        break
                    }
                }
            }
        }

        if ($client.State -ne [System.Net.WebSockets.WebSocketState]::Open) {
            break
        }

        if (-not $receiveTask) {
            $receiveTask = $client.ReceiveAsync([ArraySegment[byte]]::new($buffer), $cts.Token)
        }
        if (($receiveTask -as [IAsyncResult]).AsyncWaitHandle.WaitOne(300)) {
            $result = $receiveTask.GetAwaiter().GetResult()
            $receiveTask = $null
            if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
                break
            }
            $json = [Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count)
            $event = $json | ConvertFrom-Json
            switch ($event.event) {
                'helloAck' {
                    $trusted = [bool]$event.data.device.trusted
                    Write-Status (Get-TrustStatusText -Trusted $trusted -Paused $paused)
                    if ($trusted) {
                        Write-Log '同步连接已建立'
                    } else {
                        Write-Log '设备已连接，等待网页批准'
                    }
                }
                'clipboardSync' {
                    Write-Clipboard $event.data.text
                }
                'clipboardAck' {
                    if ($event.data.status -eq 'ok') {
                        Write-Log '文本同步成功'
                    } elseif ($event.data.status -eq 'rejected') {
                        Write-Log "文本同步被拒绝：$($event.data.reason)"
                    }
                }
                'forbidden' {
                    Write-Status '认证失败'
                    Write-Log '同步认证失败'
                }
                'deviceState' {
                    if ($event.data.deviceId -eq $Config.DeviceId -and $event.data.type -eq 'trusted') {
                        $trusted = [bool]$event.data.trusted
                        Write-Status (Get-TrustStatusText -Trusted $trusted -Paused $paused)
                        Write-Log $(if ($trusted) { '当前设备已获批准' } else { '当前设备已取消信任' })
                    }
                }
                'payloadNotice' {
                    $payloadId = [string]$event.data.payloadId
                    if ($payloadId) {
                        $receivedPayloads[$payloadId] = @{
                            payloadId = $payloadId
                            title = [string]$event.data.title
                            kind = [string]$event.data.kind
                            mime = [string]$event.data.mime
                            size = [int64]$event.data.size
                            room = [string]$event.data.room
                            sourceDeviceId = [string]$event.data.sourceDeviceId
                            actionUrl = [string]$event.data.actionUrl
                            downloadUrl = [string]$event.data.downloadUrl
                        }
                        Write-JsonEvent -Type 'payloadNotice' -Value $receivedPayloads[$payloadId]
                    }
                    Write-Log "收到 payload 通知：$($event.data.title)"
                }
            }
        }
    }
} catch {
    Write-Status '连接失败'
    Write-Log "同步连接失败：$($_.Exception.Message)"
} finally {
    try {
        if ($client.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
            $client.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'bye', [Threading.CancellationToken]::None).GetAwaiter().GetResult()
        }
    } catch {}
    $httpClient.Dispose()
    $client.Dispose()
    Write-Status '已断开'
}
