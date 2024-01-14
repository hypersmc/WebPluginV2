/*
 * ******************************************************
 *  *Copyright (c) 2020-2022. Jesper Henriksen mhypers@gmail.com
 *
 *  * This file is part of WebServer project
 *  *
 *  * WebServer can not be copied and/or distributed without the express
 *  * permission of Jesper Henriksen
 *  ******************************************************
 */ 
package me.jumpwatch.webserver.php.windows.installers;

public class WinInstaller {
    public static void WindowsPHPNginxInstaller(){
        WindowsPHPGetter.WindowsPHPGetter();
        WindowsPHPGetter.WindowsNginxGetter();
    }
}
