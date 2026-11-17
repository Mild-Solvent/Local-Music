/* Muzza.exe: starts the bundled Java runtime with the Muzza app, without a console window. */
#include <windows.h>
#include <wchar.h>

int WINAPI wWinMain(HINSTANCE h, HINSTANCE p, LPWSTR args, int show) {
    wchar_t dir[MAX_PATH];
    GetModuleFileNameW(NULL, dir, MAX_PATH);
    wchar_t *slash = wcsrchr(dir, L'\\');
    if (slash) *slash = 0;
    wchar_t cmd[8192];
    swprintf(cmd, 8192,
        L"\"%ls\\runtime\\bin\\javaw.exe\" -Xmx1g -Dmuzza.home=\"%ls\" -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar \"%ls\\app\\muzza.jar\" %ls",
        dir, dir, dir, args ? args : L"");
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    if (!CreateProcessW(NULL, cmd, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
        MessageBoxW(NULL, L"Muzza could not start. Please unzip the whole Muzza folder and run Muzza.exe from inside it.", L"Muzza", MB_ICONERROR);
        return 1;
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return 0;
}
