package com.bortbort.arduino.FiloFirmata.Parser;

/**
 * Firmata Protocol Command Bytes
 * List of all bytes used to identify specific messages sent from the Firmata device over the SerialPort
 * https://github.com/firmata/protocol/blob/master/protocol.md
 */
public enum CommandBytes {
    ANALOG_MESSAGE      (0xE0),
    DIGITAL_MESSAGE     (0x90),
    EVENT_ANALOG_PIN    (0xC0),
    EVENT_DIGITAL_PIN   (0xD0),
    SET_PIN_MODE        (0xF4),
    SET_PIN_VALUE       (0xF5),
    START_SYSEX         (0xF0),
    END_SYSEX           (0xF7);


    byte commandByte;
    CommandBytes(int commandByte) {
        this.commandByte = (byte) commandByte;
    }

    public byte getCommandByte() {
        return commandByte;
    }
}