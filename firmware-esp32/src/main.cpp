#include <Arduino.h>
// TODO : 현재 ESP8266 버전 -> ESP32 버전으로 변경 필요
#include <ESP8266WiFi.h>
#include <WiFiUdp.h>
#include <Servo.h>

const char* ssid = WIFI_SSID;
const char* password = WIFI_PASS;

WiFiUDP udpServer;
const unsigned int localUdpPort = 41234;
char incomingPacket[255];

Servo myServo;
const int servoPin = 4; // GPIO 4 (D2)

void setup() {
    Serial.begin(115200);
    delay(1000);
    myServo.attach(servoPin, 400, 2600);
    myServo.write(0);

    WiFi.begin(ssid, password);
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }

    Serial.println("\n[UDP PoC] WiFi connected.");
    Serial.print("IP address: ");
    Serial.println(WiFi.localIP());

    udpServer.begin(localUdpPort);
}

void loop() {
    int packetSize = udpServer.parsePacket();
    if (packetSize) {
        int len = udpServer.read(incomingPacket, 255);
        if (len > 0) {
            incomingPacket[len] = 0;
        }

        Serial.printf("packet: %s\n", incomingPacket);

        int angle = atoi(incomingPacket);

        if (angle >= 0 && angle <= 180) {

            myServo.write(angle);
            Serial.printf("angle: %d\n", angle);
            
            udpServer.beginPacket(udpServer.remoteIP(), udpServer.remotePort());
            udpServer.write("MOVED");
            udpServer.endPacket();
        } else {
            udpServer.beginPacket(udpServer.remoteIP(), udpServer.remotePort());
            udpServer.write("INVALID_ANGLE");
            udpServer.endPacket();
        }
    }
}