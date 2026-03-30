#!/data/data/com.neonide.studio/files/usr/bin/bash

set -e

export DEBIAN_FRONTEND=noninteractive

#APT_OPTIONS="-o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confnew"

echo "--- [1/5] Updating system packages ---"

apt update
apt upgrade -y

echo "--- [2/5] Installing dependencies ---"

apt install -y $APT_OPTIONS \
    openjdk-17 \
    git \
    wget \
    zip \
    unzip \
    jq \
    cmake \
    ninja \
    build-essential

echo "--- [3/5] Configuring Environment Variables ---"

BASHRC="$HOME/.bashrc"
touch "$BASHRC"

set_env() {
    name="$1"
    value="$2"
    if grep -q "export $name=" "$BASHRC" 2>/dev/null; then
        sed -i "s|export $name=.*|export $name=$value|" "$BASHRC"
    else
        echo "export $name=$value" >> "$BASHRC"
    fi
}

set_env "JAVA_HOME" "$PREFIX/lib/jvm/java-17-openjdk"
set_env "ANDROID_HOME" "$HOME/android-sdk"
set_env "ANDROID_SDK_ROOT" "\$ANDROID_HOME"
set_env "ANDROID_USER_HOME" "$HOME/.android"

add_path() {
    path_to_add="$1"
    if ! grep -q "$path_to_add" "$BASHRC" 2>/dev/null; then
        echo "export PATH=\$PATH:$path_to_add" >> "$BASHRC"
    fi
}

add_path "\$JAVA_HOME/bin"
add_path "\$ANDROID_HOME/platform-tools"
add_path "\$ANDROID_HOME/cmdline-tools/latest/bin"
add_path "\$ANDROID_HOME/build-tools/35.0.2"
add_path "\$HOME/androidide-tools/scripts"
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$HOME/.android"
export PATH="$PATH:$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.2"

echo "--- [4/5] Preparing up Android SDK Tools ---"

mkdir -p "$ANDROID_HOME"
mkdir -p "$ANDROID_USER_HOME"
cd "$ANDROID_HOME"

accept_all_licenses() {
    echo "Accepting all SDK licenses..."
    
    mkdir -p "$ANDROID_HOME/licenses"
    
    # Android SDK License
    cat > "$ANDROID_HOME/licenses/android-sdk-license" << 'EOF'
8933bad161af9b7d12b3de2ad09411301227335c
d56f5187479451eabf01fb78af6dfcb131a6481e
24333f8a63b6825ea9c5514f83c2829b004d1fee
84831b9409646a918e30573bab4c9c91346d8abd
d56267bc26a57ae0f8d35d727c6d28302e30f9f2
263b96ad6e128cc1a00480a0f3fa2c6a35a06584
e6b7c2ab7fa2298c15165e9583d0e8ca1e86053e
EOF

    # Android SDK Preview License
    cat > "$ANDROID_HOME/licenses/android-sdk-preview-license" << 'EOF'
84831b9409646a918e30573bab4c9c91346d8abd
504667c67f1b78f882b3a861e2c22e542c80c83e
50466765fdc039896a37ad963e494673e9e4fd98
EOF

    # Android SDK ARM DBT License
    cat > "$ANDROID_HOME/licenses/android-sdk-arm-dbt-license" << 'EOF'
859f317696f67ef3d7f30a50a5560e7834b43903
EOF

    # Google GDK License
    cat > "$ANDROID_HOME/licenses/google-gdk-license" << 'EOF'
33b6a2b64607f11b759f320ef9dff4ae5c47d97a
EOF

    # Intel Android Extra License
    cat > "$ANDROID_HOME/licenses/intel-android-extra-license" << 'EOF'
d975f751698a77b662f1254ddbeed3901e976f5a
EOF

    # Android Google TV License
    cat > "$ANDROID_HOME/licenses/android-googletv-license" << 'EOF'
601085b94cd77f0b54ff86406957099ebe79c4d6
EOF

    # MIPS Android Sys Image License
    cat > "$ANDROID_HOME/licenses/mips-android-sysimage-license" << 'EOF'
e9acab5b5fbb560a72cfaecce8946896ff6aab9d
EOF
}

create_platform_tools_metadata() {
    cat > "$ANDROID_HOME/platform-tools/source.properties" << 'EOF'
Pkg.Desc = Android SDK Platform-Tools
Pkg.Revision = 35.0.2
Pkg.Path = platform-tools
Pkg.UserSrc = false
EOF

    cat > "$ANDROID_HOME/platform-tools/package.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository
    xmlns:ns2="http://schemas.android.com/repository/android/common/02"
    xmlns:ns3="http://schemas.android.com/repository/android/common/01"
    xmlns:ns4="http://schemas.android.com/repository/android/generic/01"
    xmlns:ns5="http://schemas.android.com/repository/android/generic/02"
    xmlns:ns6="http://schemas.android.com/sdk/android/repo/addon2/01"
    xmlns:ns7="http://schemas.android.com/sdk/android/repo/addon2/02"
    xmlns:ns8="http://schemas.android.com/sdk/android/repo/repository2/01"
    xmlns:ns9="http://schemas.android.com/sdk/android/repo/repository2/02"
    xmlns:ns10="http://schemas.android.com/sdk/android/repo/sys-img2/02"
    xmlns:ns11="http://schemas.android.com/sdk/android/repo/sys-img2/01">
    <license id="android-sdk-license" type="text">Terms and Conditions</license>
    <localPackage path="platform-tools" obsolete="false">
        <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:genericDetailsType"/>
        <revision>
            <major>35</major>
            <minor>0</minor>
            <micro>2</micro>
        </revision>
        <display-name>Android SDK Platform-Tools</display-name>
        <uses-license ref="android-sdk-license"/>
    </localPackage>
</ns2:repository>
EOF
}

create_build_tools_metadata() {
    echo "Creating build-tools metadata..."
    
    # source.properties
    cat > "$ANDROID_HOME/build-tools/35.0.2/source.properties" << 'EOF'
Pkg.Desc = Android SDK Build-Tools 35.0.2
Pkg.Revision = 35.0.2
Pkg.Path = build-tools;35.0.2
Pkg.UserSrc = false
EOF

    cat > "$ANDROID_HOME/build-tools/35.0.2/package.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository
    xmlns:ns2="http://schemas.android.com/repository/android/common/02"
    xmlns:ns3="http://schemas.android.com/repository/android/common/01"
    xmlns:ns4="http://schemas.android.com/repository/android/generic/01"
    xmlns:ns5="http://schemas.android.com/repository/android/generic/02"
    xmlns:ns6="http://schemas.android.com/sdk/android/repo/addon2/01"
    xmlns:ns7="http://schemas.android.com/sdk/android/repo/addon2/02"
    xmlns:ns8="http://schemas.android.com/sdk/android/repo/repository2/01"
    xmlns:ns9="http://schemas.android.com/sdk/android/repo/repository2/02"
    xmlns:ns10="http://schemas.android.com/sdk/android/repo/sys-img2/02"
    xmlns:ns11="http://schemas.android.com/sdk/android/repo/sys-img2/01">
    <license id="android-sdk-license" type="text">Terms and Conditions</license>
    <localPackage path="build-tools;35.0.2" obsolete="false">
        <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:genericDetailsType"/>
        <revision>
            <major>35</major>
            <minor>0</minor>
            <micro>2</micro>
        </revision>
        <display-name>Android SDK Build-Tools 35.0.2</display-name>
        <uses-license ref="android-sdk-license"/>
    </localPackage>
</ns2:repository>
EOF
}

create_ndk_metadata() {
    cat > "$ANDROID_HOME/ndk/29.0.14206865/source.properties" << 'EOF'
Pkg.Desc = Android NDK
Pkg.Revision = 29.0.14206865
Pkg.Path = ndk;29.0.14206865
Pkg.UserSrc = false
EOF

    cat > "$ANDROID_HOME/ndk/29.0.14206865/package.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository
    xmlns:ns2="http://schemas.android.com/repository/android/common/02"
    xmlns:ns3="http://schemas.android.com/repository/android/common/01"
    xmlns:ns4="http://schemas.android.com/repository/android/generic/01"
    xmlns:ns5="http://schemas.android.com/repository/android/generic/02"
    xmlns:ns6="http://schemas.android.com/sdk/android/repo/addon2/01"
    xmlns:ns7="http://schemas.android.com/sdk/android/repo/addon2/02"
    xmlns:ns8="http://schemas.android.com/sdk/android/repo/repository2/01"
    xmlns:ns9="http://schemas.android.com/sdk/android/repo/repository2/02"
    xmlns:ns10="http://schemas.android.com/sdk/android/repo/sys-img2/02"
    xmlns:ns11="http://schemas.android.com/sdk/android/repo/sys-img2/01">
    <license id="android-sdk-license" type="text">Terms and Conditions</license>
    <localPackage path="ndk;29.0.14206865" obsolete="false">
        <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:genericDetailsType"/>
        <revision>
            <major>29</major>
            <minor>0</minor>
            <micro>14206865</micro>
        </revision>
        <display-name>Android NDK 29.0.14206865</display-name>
        <uses-license ref="android-sdk-license"/>
    </localPackage>
</ns2:repository>
EOF
}

if [ ! -d "cmdline-tools/latest/bin" ]; then
    echo "Downloading official Android Command Line Tools..."
    
    TEMP_DIR=$(mktemp -d)
    cd "$TEMP_DIR"
    
    wget -q --show-progress https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
    unzip -oq commandlinetools-linux-13114758_latest.zip
    
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    mv cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
    
    cd "$ANDROID_HOME"
    rm -rf "$TEMP_DIR"
    
    chmod +x "$ANDROID_HOME/cmdline-tools/latest/bin/"* 2>/dev/null || true
    
    echo "Command Line Tools installed successfully!"
fi

accept_all_licenses

echo "Accepting licenses via sdkmanager..."
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses > /dev/null 2>&1 || true

# Download static platform-tools and build-tools (Termux compatible)
if [ ! -d "platform-tools" ] || [ ! -d "build-tools/35.0.2" ]; then
    echo "Downloading static Android SDK tools (compatible with Termux)..."
    
    TEMP_DIR=$(mktemp -d)
    cd "$TEMP_DIR"
    
    wget -q --show-progress https://github.com/lzhiyong/android-sdk-tools/releases/download/35.0.2/android-sdk-tools-static-aarch64.zip
    unzip -oq android-sdk-tools-static-aarch64.zip
    
    # Create proper directory structure
    mkdir -p "$ANDROID_HOME/platform-tools"
    mkdir -p "$ANDROID_HOME/build-tools/35.0.2"
    mkdir -p "$ANDROID_HOME/others"
    
    # Move platform-tools folder contents
    if [ -d "platform-tools" ]; then
        echo "Moving platform-tools..."
        cp -rf platform-tools/* "$ANDROID_HOME/platform-tools/"
    fi
    
    # Move build-tools folder contents to 35.0.2
    if [ -d "build-tools" ]; then
        echo "Moving build-tools to 35.0.2..."
        cp -rf build-tools/* "$ANDROID_HOME/build-tools/35.0.2/"
    fi
    
    # Move others folder contents
    if [ -d "others" ]; then
        echo "Moving other tools..."
        cp -rf others/* "$ANDROID_HOME/others/"
    fi
    
    # Cleanup temp directory
    cd "$ANDROID_HOME"
    rm -rf "$TEMP_DIR"
    
    # Make all tools executable
    chmod +x "$ANDROID_HOME/platform-tools/"* 2>/dev/null || true
    chmod +x "$ANDROID_HOME/build-tools/35.0.2/"* 2>/dev/null || true
    chmod +x "$ANDROID_HOME/others/"* 2>/dev/null || true
    
    echo "Static SDK tools organized successfully!"
fi

# Create metadata files for SDK components
create_platform_tools_metadata
create_build_tools_metadata

# Download NDK if missing
if [ ! -d "ndk/29.0.14206865" ]; then
    echo "Downloading Android NDK (Termux compatible)..."
    cd "$ANDROID_HOME"
    wget -q --show-progress https://github.com/AndroidCSOfficial/acs-build-system/releases/download/v29.0.14033849/android-ndk-r29-aarch64-linux-android.tar.xz
    tar -xJf android-ndk-r29-aarch64-linux-android.tar.xz
    mkdir -p ndk
    mv android-ndk-r29 ndk/29.0.14206865
    rm -f android-ndk-r29-aarch64-linux-android.tar.xz
fi

# Create NDK metadata
create_ndk_metadata

echo "--- [5/5] Finalizing ---"

# Make androidide-tools executable
if [ -d "$HOME/androidide-tools/scripts" ]; then
    chmod +x "$HOME/androidide-tools/scripts/"*
fi

# Verify licenses are accepted
echo "Verifying licenses..."
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses > /dev/null 2>&1 || true

# Verify SDK installation
echo "Verifying SDK installation..."
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --list_installed 2>/dev/null || true

# Source bashrc for current session
. "$BASHRC" 2>/dev/null || true