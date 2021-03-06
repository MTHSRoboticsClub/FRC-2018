package frc.team1778.FreezyDrive;

import edu.wpi.first.wpilibj.Joystick;
import frc.team1778.Utility.InterLinkElite;

public class Controller {

  // Types: 0 = Logitech F310, 1 = InterLink Flight Controller, 2 = Logitech Dual Action
  public static final int DRIVER_CONTROLLER_TYPE = 1;
  // public static final int COPILOT_CONTROLLER_TYPE = 1;
  public static final int PORT_DRIVER_CONTROLLER = 0;
  // public static final int PORT_COPILOT_CONTROLLER = 1;
  public static Joystick Driver;
  public static Joystick CoPilot;

  public static boolean[] controllerType = new boolean[3];

  public static boolean initialized = false;

  public static void initialize() {

    if (initialized) return;

    Driver = new Joystick(PORT_DRIVER_CONTROLLER);
    // CoPilot = new Joystick(PORT_COPILOT_CONTROLLER);

    initialized = true;
  }

  public static double Driver_Throttle() {
    // New (2018) Interlink
    return Driver.getRawAxis(InterLinkElite.Axis.LEFT_Y);

    // Legacy (2017) Interlink
    // return -1.0*Driver.getRawAxis(InterLinkElite.Axis.LEFT_Y);
  }

  public static double Driver_Steering() {
    return -1 * Driver.getRawAxis(InterLinkElite.Axis.RIGHT_X);
  }

  public static boolean Driver_isQuickTurn() {
    // New (2018) Interlink
    return Driver.getRawButton(InterLinkElite.RIGHT_SWITCH);

    // Legacy (2017) Interlink
    // return !Driver.getRawButton(InterLinkElite.RIGHT_SWITCH);
  }

  public static boolean Driver_isLowSensitivity() {
    return Driver.getRawButton(5);
  }
}
