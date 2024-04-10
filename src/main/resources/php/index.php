<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Exciting PHP Website</title>
    <style>
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background-color: #f9f9f9;
            margin: 0;
            padding: 0;
            color: #333;
        }

        header {
            background-color: #1a1a1a;
            color: #fff;
            padding: 20px;
            text-align: center;
        }

        .container {
            width: 80%;
            margin: auto;
            overflow: hidden;
        }

        article {
            background-color: #fff;
            border: 1px solid #ddd;
            border-radius: 5px;
            margin: 20px 0;
            padding: 20px;
            box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
        }

        h2 {
            color: #333;
        }

        p {
            line-height: 1.6;
        }

        .date {
            color: #888;
        }
    </style>
</head>
<body>

<header>
    <h1>Exciting PHP Website</h1>
</header>

<div class="container">

    <?php
    // Sample articles
    $articles = array(
        array(
            'title' => 'Exploring the Wonders of PHP',
            'description' => 'Discover the amazing features and capabilities of PHP for web development.',
            'date' => '2024-01-14',
        ),
        array(
            'title' => 'Building Dynamic Websites with PHP and MySQL',
            'description' => 'Learn how to create dynamic and interactive websites using PHP and MySQL.',
            'date' => '2024-01-15',
        ),
        array(
            'title' => 'Mastering CSS for Stunning Web Designs',
            'description' => 'Enhance your web design skills by mastering the art of CSS styling.',
            'date' => '2024-01-16',
        ),
        array(
                'title' => 'Using Java Classes in PHP',
                'description' => 'Explore the integration of Java classes into PHP applications for enhanced functionality by using "import path.to.java.class" without quotes ',
                'date' => '2024-04-08',
            ),
    );

    // Display articles
    foreach ($articles as $article) {
        echo "<article>";
        echo "<h2>{$article['title']}</h2>";
        echo "<p>{$article['description']}</p>";
        echo "<p class='date'>Published on {$article['date']}</p>";
        echo "</article>";
    }
    ?>

</div>

</body>
</html>