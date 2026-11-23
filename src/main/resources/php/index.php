<?php
declare(strict_types=1);

function e(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function getGreeting(): string
{
    $hour = (int) date('G');

    if ($hour >= 5 && $hour < 12) {
        return 'Good morning!';
    }

    if ($hour >= 12 && $hour < 18) {
        return 'Good afternoon!';
    }

    if ($hour >= 18 && $hour < 22) {
        return 'Good evening!';
    }

    return 'Good night!';
}

function getQuote(): string
{
    $quotes = [
        'The best way to predict the future is to invent it.',
        'Small modules make big systems easier to maintain.',
        'A working runtime is better than a guessed runtime.',
        'If it can be enabled, it should be testable.',
    ];

    return $quotes[array_rand($quotes)];
}

function addonEnabled(array $addon): bool
{
    if (isset($addon['extension']) && !extension_loaded($addon['extension'])) {
        return false;
    }

    foreach (($addon['functions'] ?? []) as $function) {
        if (!function_exists($function)) {
            return false;
        }
    }

    foreach (($addon['classes'] ?? []) as $class) {
        if (!class_exists($class)) {
            return false;
        }
    }

    return true;
}

function iniValue(string $key): string
{
    $value = ini_get($key);

    if ($value === false || $value === '') {
        return 'N/A';
    }

    return $value;
}

$addons = [
    'Core / Required' => [
        [
            'name' => 'JSON',
            'extension' => 'json',
            'required' => true,
            'description' => 'JSON encode/decode support.',
        ],
        [
            'name' => 'PCRE',
            'extension' => 'pcre',
            'required' => true,
            'description' => 'Regular expression support.',
        ],
        [
            'name' => 'Tokenizer',
            'extension' => 'tokenizer',
            'required' => true,
            'description' => 'PHP tokenizer support.',
        ],
        [
            'name' => 'Session',
            'extension' => 'session',
            'required' => true,
            'description' => 'PHP session support.',
        ],
    ],

    'Text / Locale' => [
        [
            'name' => 'mbstring',
            'extension' => 'mbstring',
            'required' => true,
            'description' => 'Multibyte string support.',
        ],
        [
            'name' => 'mbregex',
            'extension' => 'mbstring',
            'functions' => ['mb_ereg'],
            'required' => true,
            'description' => 'Multibyte regex support. Provided by mbstring.',
        ],
        [
            'name' => 'intl',
            'extension' => 'intl',
            'classes' => ['IntlDateFormatter'],
            'required' => true,
            'description' => 'Internationalization support.',
        ],
        [
            'name' => 'iconv',
            'extension' => 'iconv',
            'required' => false,
            'description' => 'Character encoding conversion.',
        ],
    ],

    'Web / Network' => [
        [
            'name' => 'OpenSSL',
            'extension' => 'openssl',
            'required' => true,
            'description' => 'TLS, certificates and crypto support.',
        ],
        [
            'name' => 'cURL',
            'extension' => 'curl',
            'required' => false,
            'description' => 'HTTP client support.',
        ],
        [
            'name' => 'Fileinfo',
            'extension' => 'fileinfo',
            'required' => false,
            'description' => 'MIME/file type detection.',
        ],
    ],

    'Database' => [
        [
            'name' => 'PDO',
            'extension' => 'PDO',
            'required' => false,
            'description' => 'Base PDO database layer.',
        ],
        [
            'name' => 'PDO SQLite',
            'extension' => 'pdo_sqlite',
            'required' => false,
            'description' => 'SQLite support through PDO.',
        ],
        [
            'name' => 'SQLite3',
            'extension' => 'sqlite3',
            'required' => false,
            'description' => 'Native SQLite3 support.',
        ],
        [
            'name' => 'PDO MySQL',
            'extension' => 'pdo_mysql',
            'required' => false,
            'description' => 'MySQL/MariaDB support through PDO.',
        ],
        [
            'name' => 'MySQLi',
            'extension' => 'mysqli',
            'required' => false,
            'description' => 'Native MySQL/MariaDB support.',
        ],
    ],

    'Images / Files' => [
        [
            'name' => 'GD',
            'extension' => 'gd',
            'required' => false,
            'description' => 'Image manipulation support.',
        ],
        [
            'name' => 'ZIP',
            'extension' => 'zip',
            'required' => false,
            'description' => 'ZIP archive support.',
        ],
        [
            'name' => 'Phar',
            'extension' => 'Phar',
            'required' => false,
            'description' => 'PHP archive support.',
        ],
    ],

    'XML' => [
        [
            'name' => 'XML',
            'extension' => 'xml',
            'required' => false,
            'description' => 'Base XML support.',
        ],
        [
            'name' => 'DOM',
            'extension' => 'dom',
            'required' => false,
            'description' => 'DOM document parsing support.',
        ],
        [
            'name' => 'SimpleXML',
            'extension' => 'SimpleXML',
            'required' => false,
            'description' => 'Simple XML parsing support.',
        ],
        [
            'name' => 'XMLReader',
            'extension' => 'xmlreader',
            'required' => false,
            'description' => 'Streaming XML reader.',
        ],
        [
            'name' => 'XMLWriter',
            'extension' => 'xmlwriter',
            'required' => false,
            'description' => 'Streaming XML writer.',
        ],
    ],

    'Performance' => [
        [
            'name' => 'OPcache',
            'extension' => 'Zend OPcache',
            'required' => false,
            'description' => 'PHP bytecode cache.',
        ],
        [
            'name' => 'APCu',
            'extension' => 'apcu',
            'required' => false,
            'description' => 'Userland memory cache.',
        ],
    ],
];

$totalAddons = 0;
$enabledAddons = 0;
$missingRequired = [];

foreach ($addons as $group) {
    foreach ($group as $addon) {
        $totalAddons++;

        if (addonEnabled($addon)) {
            $enabledAddons++;
            continue;
        }

        if ($addon['required']) {
            $missingRequired[] = $addon['name'];
        }
    }
}

$runtimeOk = count($missingRequired) === 0;

$phpVersion = PHP_VERSION;
$sapiName = PHP_SAPI;
$serverSoftware = $_SERVER['SERVER_SOFTWARE'] ?? 'N/A';
$currentDateTime = date('l, F j, Y \a\t H:i:s T');
$loadedIni = php_ini_loaded_file() ?: 'N/A';
$scannedIni = php_ini_scanned_files() ?: 'N/A';
$extensionDir = iniValue('extension_dir');
$opcacheEnabled = extension_loaded('Zend OPcache') && (bool) ini_get('opcache.enable');
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>WebPlugin example site</title>

    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #f4f4f4;
            color: #333;
            margin: 0;
            padding: 20px;
        }

        .container {
            max-width: 980px;
            margin: 0 auto;
            padding: 24px;
            background-color: #fff;
            border-radius: 8px;
            box-shadow: 0 0 10px rgba(0, 0, 0, 0.10);
        }

        h1 {
            color: #2c3e50;
            text-align: center;
            margin-bottom: 8px;
        }

        h2 {
            color: #2c3e50;
            margin-top: 28px;
            border-bottom: 1px solid #ddd;
            padding-bottom: 8px;
        }

        h3 {
            color: #2c3e50;
            margin-top: 22px;
            margin-bottom: 8px;
        }

        .intro {
            text-align: center;
            margin-bottom: 20px;
        }

        blockquote {
            font-style: italic;
            color: #7f8c8d;
            margin: 20px 0;
            text-align: center;
        }

        .status-box {
            padding: 12px 14px;
            border-radius: 6px;
            margin: 18px 0;
            border: 1px solid;
        }

        .status-box.ok {
            background-color: #eafaf1;
            border-color: #b7e4c7;
            color: #1e7e34;
        }

        .status-box.warning {
            background-color: #fff8e1;
            border-color: #ffe082;
            color: #8a6d00;
        }

        .summary {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
            gap: 12px;
            margin: 20px 0;
        }

        .summary-card {
            background-color: #f8f9fa;
            border: 1px solid #ddd;
            border-radius: 6px;
            padding: 14px;
            min-width: 0;
        }

        .summary-card .label {
            color: #7f8c8d;
            font-size: 0.9em;
            margin-bottom: 5px;
        }

        .summary-card .value {
            font-size: 1.2em;
            font-weight: bold;
            color: #2c3e50;
            overflow-wrap: anywhere;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            margin: 12px 0;
            table-layout: fixed;
        }

        th,
        td {
            padding: 10px;
            border: 1px solid #ddd;
            text-align: left;
            vertical-align: top;
            overflow-wrap: anywhere;
            word-break: break-word;
        }

        th {
            background-color: #f2f2f2;
            color: #2c3e50;
            width: 220px;
        }

        .addon-table th:nth-child(1) {
            width: 180px;
        }

        .addon-table th:nth-child(2) {
            width: 110px;
        }

        .addon-table th:nth-child(3) {
            width: 100px;
        }

        .status {
            display: inline-block;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 0.85em;
            font-weight: bold;
            white-space: nowrap;
        }

        .status.enabled {
            background-color: #eafaf1;
            color: #1e7e34;
            border: 1px solid #b7e4c7;
        }

        .status.missing {
            background-color: #fdecea;
            color: #b02a37;
            border: 1px solid #f5c2c7;
        }

        .status.disabled {
            background-color: #fff8e1;
            color: #8a6d00;
            border: 1px solid #ffe082;
        }

        .small {
            color: #7f8c8d;
            font-size: 0.9em;
        }

        code {
            background-color: #f2f2f2;
            padding: 2px 5px;
            border-radius: 4px;
        }

        footer {
            margin-top: 24px;
            font-size: 0.9em;
            color: #7f8c8d;
            text-align: center;
        }

        @media (max-width: 700px) {
            body {
                padding: 10px;
            }

            .container {
                padding: 16px;
            }

            table,
            tbody,
            tr,
            th,
            td {
                display: block;
                width: 100%;
            }

            th {
                border-bottom: 0;
            }

            td {
                border-top: 0;
            }

            .addon-table th:nth-child(1),
            .addon-table th:nth-child(2),
            .addon-table th:nth-child(3) {
                width: 100%;
            }
        }
    </style>
</head>

<body>
    <div class="container">
        <h1><?= e(getGreeting()) ?></h1>

        <p class="intro">
            Welcome to WebPlugin's simple PHP runtime test site.
        </p>

        <blockquote>
            "<?= e(getQuote()) ?>"
        </blockquote>

        <div class="status-box <?= $runtimeOk ? 'ok' : 'warning' ?>">
            <?php if ($runtimeOk): ?>
                Required PHP addons are available.
            <?php else: ?>
                Missing required PHP addons:
                <strong><?= e(implode(', ', $missingRequired)) ?></strong>
            <?php endif; ?>
        </div>

        <div class="summary">
            <div class="summary-card">
                <div class="label">PHP Version</div>
                <div class="value"><?= e($phpVersion) ?></div>
            </div>

            <div class="summary-card">
                <div class="label">SAPI Name</div>
                <div class="value"><?= e($sapiName) ?></div>
            </div>

            <div class="summary-card">
                <div class="label">Enabled Addons</div>
                <div class="value"><?= e((string) $enabledAddons) ?> / <?= e((string) $totalAddons) ?></div>
            </div>

            <div class="summary-card">
                <div class="label">OPcache</div>
                <div class="value"><?= $opcacheEnabled ? 'Enabled' : 'Disabled' ?></div>
            </div>
        </div>

        <h2>PHP Information</h2>

        <table>
            <tr>
                <th>Current Date and Time</th>
                <td><?= e($currentDateTime) ?></td>
            </tr>
            <tr>
                <th>Server Software</th>
                <td><?= e($serverSoftware) ?></td>
            </tr>
            <tr>
                <th>Loaded php.ini</th>
                <td><?= e($loadedIni) ?></td>
            </tr>
            <tr>
                <th>Scanned ini files</th>
                <td><?= e($scannedIni) ?></td>
            </tr>
            <tr>
                <th>Extension Directory</th>
                <td><?= e($extensionDir) ?></td>
            </tr>
        </table>

        <h2>Important PHP Settings</h2>

        <table>
            <tr>
                <th>memory_limit</th>
                <td><?= e(iniValue('memory_limit')) ?></td>
            </tr>
            <tr>
                <th>upload_max_filesize</th>
                <td><?= e(iniValue('upload_max_filesize')) ?></td>
            </tr>
            <tr>
                <th>post_max_size</th>
                <td><?= e(iniValue('post_max_size')) ?></td>
            </tr>
            <tr>
                <th>max_execution_time</th>
                <td><?= e(iniValue('max_execution_time')) ?> seconds</td>
            </tr>
        </table>

        <p class="small">
            This page does not use <code>phpinfo()</code>, because that exposes too much internal server information for a default example page.
        </p>

        <h2>Modular PHP Addon Status</h2>

        <p class="small">
            Required addons should be available in the WebPlugin runtime. Optional addons can be enabled or disabled depending on the selected modular build.
        </p>

        <?php foreach ($addons as $groupName => $groupAddons): ?>
            <h3><?= e($groupName) ?></h3>

            <table class="addon-table">
                <tr>
                    <th>Addon</th>
                    <th>Status</th>
                    <th>Type</th>
                    <th>Description</th>
                </tr>

                <?php foreach ($groupAddons as $addon): ?>
                    <?php
                    $enabled = addonEnabled($addon);
                    $required = (bool) $addon['required'];

                    if ($enabled) {
                        $statusClass = 'enabled';
                        $statusText = 'Enabled';
                    } elseif ($required) {
                        $statusClass = 'missing';
                        $statusText = 'Missing';
                    } else {
                        $statusClass = 'disabled';
                        $statusText = 'Disabled';
                    }
                    ?>

                    <tr>
                        <td>
                            <strong><?= e($addon['name']) ?></strong>
                            <?php if (isset($addon['extension'])): ?>
                                <br>
                                <span class="small">
                                    Extension: <code><?= e($addon['extension']) ?></code>
                                </span>
                            <?php endif; ?>
                        </td>
                        <td>
                            <span class="status <?= e($statusClass) ?>">
                                <?= e($statusText) ?>
                            </span>
                        </td>
                        <td><?= $required ? 'Required' : 'Optional' ?></td>
                        <td><?= e($addon['description']) ?></td>
                    </tr>
                <?php endforeach; ?>
            </table>
        <?php endforeach; ?>

        <footer>
            Current Date and Time: <?= e($currentDateTime) ?><br>
            WebPlugin example site · PHP <?= e($phpVersion) ?>
        </footer>
    </div>
</body>
</html>
