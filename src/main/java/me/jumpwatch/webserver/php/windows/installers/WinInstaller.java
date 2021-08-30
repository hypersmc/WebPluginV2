package me.jumpwatch.webserver.php.windows.installers;

public class WinInstaller {
    public static void WindowsPHPNginxInstaller(){
        WindowsPHPGetter.WindowsPHPGetter();
        WindowsPHPGetter.WindowsNginxGetter();
    }
}
