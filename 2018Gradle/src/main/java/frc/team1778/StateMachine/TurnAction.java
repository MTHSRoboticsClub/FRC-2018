package frc.team1778.StateMachine;

import frc.team1778.Systems.DriveAssembly;
import frc.team1778.Systems.NavXSensor;
import java.util.prefs.Preferences;

public class TurnAction extends Action {

  private double angleToTurn = 0.0;
  private double speedToTurn = 0.3;
  private boolean resetGyro = true;
  private double headingDeg = 0.0; // heading to use if not resetting gyro
  private double initialAngle = 0.0;

  public TurnAction(double angleToTurn, double speed, boolean resetGyro) {
    this.name = "<Turn Action>";
    this.angleToTurn = angleToTurn; // absolute heading to use if not resetting gyro
    this.speedToTurn = speed;
    this.resetGyro = resetGyro;

    DriveAssembly.initialize();
    NavXSensor.initialize();
  }

  public TurnAction(String name, double angleToTurn, double speed, boolean resetGyro) {
    this.name = name;
    this.angleToTurn = angleToTurn; // absolute heading to use if not resetting gyro
    this.speedToTurn = speed;
    this.resetGyro = resetGyro;

    DriveAssembly.initialize();
    NavXSensor.initialize();
  }

  // action entry
  public void initialize() {

    // if we're not resetting the gyro, we'll want to see what angle it is to start
    if (resetGyro) {
      NavXSensor.reset();
      initialAngle = 0.0;
    } else initialAngle = NavXSensor.getAngle();

    // initialize motor assembly for auto
    DriveAssembly.autoInit(resetGyro, initialAngle, false);

    super.initialize();
  }

  // called periodically
  public void process() {

    // check the difference from our initial angle
    double angleDiff = angleToTurn - initialAngle;

    // rotate to close the gap
    if (angleDiff > 0.0) DriveAssembly.rotateRight(speedToTurn);
    else DriveAssembly.rotateLeft(speedToTurn);

    super.process();
  }

  // action cleanup and exit
  public void cleanup() {
    // do some drivey cleanup

    // PWMDriveAssembly not supported

    DriveAssembly.autoStop();

    // cleanup base class
    super.cleanup();
  }

  public void persistWrite(int counter, Preferences prefs) {

    // create node for action
    Preferences actionPrefs = prefs.node(counter + "_" + this.name);

    // store action details
    actionPrefs.put("class", this.getClass().toString());
    actionPrefs.putDouble("angleToTurn", this.angleToTurn);
    actionPrefs.putDouble("speedToTurn", this.speedToTurn);
  }
}
