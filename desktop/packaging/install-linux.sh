#!/bin/sh
# Adds Muzza to the app menu (run once from inside the unpacked Muzza folder).
HERE="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
mkdir -p "$HOME/.local/share/applications"
cat > "$HOME/.local/share/applications/muzza.desktop" <<EOD
[Desktop Entry]
Type=Application
Name=Muzza
Comment=Offline music player
Exec="$HERE/muzza" %U
Icon=$HERE/muzza-icon.png
Categories=AudioVideo;Audio;Player;
Terminal=false
EOD
echo "Muzza is in your app menu now."
