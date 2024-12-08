# H264/5 to Surface Demo

**Some source code inspiration comes from**:[Decode H264 using a MediaCodec - Tech Series 001](https://www.youtube.com/watch?app=desktop&v=5mzVaWtxwos)(www.appsinthesky.com/public/h264tosurfacedemo.zip)

**No offense, please contact us immediately.**

---

## Build Environment

### Android Studio

```tex
Android Studio Ladybug | 2024.2.1 Patch 1
Build #AI-242.23339.11.2421.12483815, built on October 11, 2024
Runtime version: 21.0.3+-79915917-b509.11 aarch64
VM: OpenJDK 64-Bit Server VM by JetBrains s.r.o.
Toolkit: sun.lwawt.macosx.LWCToolkit
macOS 15.1.1
GC: G1 Young Generation, G1 Concurrent GC, G1 Old Generation
Memory: 2048M
Cores: 8
Metal Rendering is ON
Registry:
  ide.experimental.ui=true
  i18n.locale=
  terminal.new.ui=true
```





---

## How to use？

change your codec PC serve IP on **MainActivity.java**

```java
private static final String SERVER_IP = "192.168.77.124";
```

Then,**Build --> Build App Bundle(s)/ APK(s)** find **H264ToSurfaceDemo.apk** file install your Android device.

*Remember you must start your encoder first and then start the decoder.*