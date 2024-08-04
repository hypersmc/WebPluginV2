<?php
// PHP Functions

function getGreeting() {
    $hour = date('G');
    if ($hour >= 5 && $hour < 12) {
        return "Good morning!";
    } elseif ($hour >= 12 && $hour < 18) {
        return "Good afternoon!";
    } elseif ($hour >= 18 && $hour < 22) {
        return "Good evening!";
    } else {
        return "Good night!";
    }
}

function getQuote() {
    $quotes = [
        "The best way to predict the future is to invent it.",
        "To be yourself in a world that is constantly trying to make you something else is the greatest accomplishment.",
        "In three words I can sum up everything I've learned about life: it goes on."
    ];
    return $quotes[array_rand($quotes)];
}

// Get current date and time
function getCurrentDateTime() {
    return date('l, F j, Y \a\t g:i A');
}

// Gather PHP information
$phpVersion = phpversion();
$sapiName = php_sapi_name();
$serverSoftware = isset($_SERVER['SERVER_SOFTWARE']) ? $_SERVER['SERVER_SOFTWARE'] : 'N/A';

$greeting = getGreeting();
$quote = getQuote();
$currentDateTime = getCurrentDateTime();
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
            text-align: center;
            margin: 0;
            padding: 0;
        }
        .container {
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
            background-color: #fff;
            border-radius: 8px;
            box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
        }
        h1 {
            color: #2c3e50;
        }
        blockquote {
            font-style: italic;
            color: #7f8c8d;
            margin: 20px 0;
        }
        footer {
            margin-top: 20px;
            font-size: 0.9em;
            color: #7f8c8d;
        }
        .php-info {
            text-align: left;
            margin-top: 20px;
        }
        .php-info table {
            width: 100%;
            border-collapse: collapse;
            margin: 0 auto;
        }
        .php-info th, .php-info td {
            padding: 10px;
            border: 1px solid #ddd;
        }
        .php-info th {
            background-color: #f2f2f2;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1><?php echo $greeting; ?></h1>
        <p>Welcome to WebPlugin's simple PHP site!</p>
        <blockquote>
            "<?php echo $quote; ?>"
        </blockquote>
        <footer>
            <p>Current Date and Time: <?php echo $currentDateTime; ?></p>
        </footer>

        <!-- PHP Information Section -->
        <div class="php-info">
            <h2>PHP Information</h2>
            <table>
                <tr>
                    <th>PHP Version</th>
                    <td><?php echo $phpVersion; ?></td>
                </tr>
                <tr>
                    <th>SAPI Name</th>
                    <td><?php echo $sapiName; ?></td>
                </tr>
                <tr>
                    <th>Server Software</th>
                    <td><?php echo $serverSoftware; ?></td>
                </tr>
            </table>
        </div>
    </div>
</body>
</html>