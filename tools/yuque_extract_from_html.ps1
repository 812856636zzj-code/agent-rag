param(
  [Parameter(Mandatory = $true)]
  [string]$InputHtml,

  [Parameter(Mandatory = $true)]
  [string]$OutputMarkdown
)

$html = Get-Content -Raw -Encoding UTF8 -Path $InputHtml

$title = [regex]::Match($html, '<title[^>]*>(.*?)</title>', 'IgnoreCase,Singleline').Groups[1].Value
$title = [System.Net.WebUtility]::HtmlDecode($title)
$title = ($title -replace '\s*-\s*语雀.*$', '').Trim()
if ([string]::IsNullOrWhiteSpace($title)) {
  $title = 'yuque-doc'
}

$content = $html
$content = [regex]::Replace($content, '<(script|style|noscript|svg)\b[^>]*>.*?</\1>', '', 'IgnoreCase,Singleline')
$content = [regex]::Replace($content, '<pre\b[^>]*>(.*?)</pre>', {
  param($m)
  "`n`n``` `n$([System.Net.WebUtility]::HtmlDecode(($m.Groups[1].Value -replace '<[^>]+>', '')))`n``` `n`n"
}, 'IgnoreCase,Singleline')
$content = [regex]::Replace($content, '<li\b[^>]*>', "`n- ", 'IgnoreCase')
$content = [regex]::Replace($content, '</?(p|div|section|article|main|blockquote|tr|table|ul|ol|h[1-6])\b[^>]*>', "`n", 'IgnoreCase')
$content = [regex]::Replace($content, '<br\s*/?>', "`n", 'IgnoreCase')
$content = [regex]::Replace($content, '<img\b[^>]*src=["'']?([^"''>\s]+)[^>]*>', '![]($1)', 'IgnoreCase')
$content = [regex]::Replace($content, '<[^>]+>', '')
$content = [System.Net.WebUtility]::HtmlDecode($content)
$content = $content -replace "`r", ''
$content = [regex]::Replace($content, "[ `t]{2,}", ' ')
$content = [regex]::Replace($content, "`n[ `t]+", "`n")
$content = [regex]::Replace($content, "`n{3,}", "`n`n").Trim()

$markdown = "# $title`n`n$content`n"
Set-Content -Encoding UTF8 -Path $OutputMarkdown -Value $markdown
Write-Host "Wrote $OutputMarkdown"
