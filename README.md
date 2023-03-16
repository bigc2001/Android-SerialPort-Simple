Step 1. Add the JitPack repository to your build file
Add it in your root build.gradle at the end of repositories:

	allprojects {
		repositories {
			...
			maven { url 'https://www.jitpack.io' }
		}
	}

Step 2. Add the dependency
dependencies {
implementation 'com.github.bigc2001:Android-SerialPort-Simple:1.0'
}

Sample Code:

SerialPort serialPort = new SerialPort(new File("/dev/ttyS1"), 9600);
serialPort.open();
....
serialPort.close();