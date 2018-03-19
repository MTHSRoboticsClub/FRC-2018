package Systems;

import com.ctre.phoenix.motorcontrol.can.TalonSRX;
import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.LimitSwitchNormal;
import com.ctre.phoenix.motorcontrol.LimitSwitchSource;
import com.ctre.phoenix.motorcontrol.NeutralMode;

import NetworkComm.InputOutputComm;
import NetworkComm.RPIComm;
import Utility.HardwareIDs;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.Spark;

public class CubeManagement {
	
	private static boolean initialized = false;
	private static final int TIMEOUT_MS = 0;  // set to zero if skipping confirmation
	private static final int PIDLOOP_IDX = 0;  // set to zero if primary loop
	private static final int PROFILE_SLOT = 0;

	// motor polarity
	public static final boolean RIGHT_COLLECTOR_REVERSE_MOTOR = false; 
	public static final boolean LEFT_COLLECTOR_REVERSE_MOTOR = false; 
			
	// collector strength (%VBus - max is 1.0)
	private static final double COLLECTOR_MAX_STRENGTH = 0.75;  // teleop
	private static final double COLLECTOR_AUTO_STRENGTH = 0.5;  // auto
	//private static final double COLLECTOR_OUT_STRENGTH = -0.75;
	private static final double COLLECTOR_IN_POLARITY = 1.0;
	private static final double COLLECTOR_OUT_POLARITY = -1.0;
	
	private static final double COLLECTOR_IN_AUTO_STRENGTH = 0.5;  // auto
	private static final double COLLECTOR_OUT_AUTO_STRENGTH = -0.75;  // auto
		
	private static final boolean LEFT_COLLECTOR_INVERTED = true;
	private static final boolean RIGHT_COLLECTOR_INVERTED = false;

	// teleop lift strength (%VBus - max is 1.0)
	private static final double LIFT_MOTOR_UP_FACTOR = 0.75;
	private static final double LIFT_MOTOR_DOWN_FACTOR = 0.10;
	private static final double LIFT_MOTOR_DEAD_ZONE = 0.1;
		
	// brake motor strength (%VBus - max is 1.0)
	private static final double BRAKE_ON_STRENGTH = 0.5;
	private static final double BRAKE_OFF_STRENGTH = -0.5;
	
	// control dead zone threshold
	private static final double DEAD_ZONE_THRESHOLD = 0.05;
	
	// collector intake motors
	private static Spark leftCollectorMotor, rightCollectorMotor; 
	
	// lift motors
	private static TalonSRX upperLiftMotor, lowerLiftMotor;
	private static final boolean UPPER_REVERSE_MOTOR = true;
	private static final boolean LOWER_REVERSE_MOTOR = true;
			
	// brake motor
	private static boolean liftBrakeOn = false;
	private static final boolean BRAKE_MOTOR_INVERTED = false;
	private static Spark brakeMotor;
	private static double brakeStartTimer = 0;
	private static final double BRAKE_LIMIT_USEC = 500000;
	
	private static Joystick gamepad;
		
	// wait 0.25 s between button pushes on shooter
    private static final int TRIGGER_CYCLE_WAIT_US = 250000;
    private static double initTriggerTime;

	public static void initialize() {
		
		if (initialized)
			return;
		
		InputOutputComm.initialize();
		
		// reset timers
		initTriggerTime = 0;
		brakeStartTimer = 0;
		
		// create and initialize collector motors (open-loop)
		leftCollectorMotor = new Spark(HardwareIDs.LEFT_COLLECTOR_PWM_ID);
		leftCollectorMotor.setInverted(LEFT_COLLECTOR_INVERTED);
		rightCollectorMotor = new Spark(HardwareIDs.RIGHT_COLLECTOR_PWM_ID);
		rightCollectorMotor.setInverted(RIGHT_COLLECTOR_INVERTED);
        
		// create and initialize brake motor (open-loop)
		brakeMotor = new Spark(HardwareIDs.BRAKE_MOTOR_PWM_ID);
		brakeMotor.setInverted(BRAKE_MOTOR_INVERTED);
		
		// create and initialize upper lift motor (closed-loop)
		//upperLiftMotor = configureMotor(HardwareIDs.UPPER_LIFT_TALON_ID, UPPER_REVERSE_MOTOR, ALIGNED_SENSOR, kP, kI, kD, kF);
		upperLiftMotor = configureMotor(HardwareIDs.UPPER_LIFT_TALON_ID, UPPER_REVERSE_MOTOR);

		// create and initialize lower lift motor (follows upper motor)
		lowerLiftMotor = configureMotor(HardwareIDs.LOWER_LIFT_TALON_ID, LOWER_REVERSE_MOTOR, HardwareIDs.UPPER_LIFT_TALON_ID);
				
		// make sure all motors are off
		resetMotors();
		
		// reset position of encoders
		resetPos();
		
		// turn on brake
		liftBrakeOn();
		
		gamepad = new Joystick(HardwareIDs.GAMEPAD_ID);
		
		initialized = true;
	}
	
	public static void resetMotors()
	{	
		// reset collector motors
		//leftCollectorMotor.set(0);
		//rightCollectorMotor.set(0);	
		leftCollectorMotor.set(COLLECTOR_IN_AUTO_STRENGTH);      // low-level default collection
		rightCollectorMotor.set(COLLECTOR_IN_AUTO_STRENGTH);	
		
		// reset upper lift motor (lower lift follows)
		upperLiftMotor.set(ControlMode.PercentOutput, 0);
		
		// set lift brake
		liftBrakeOn();
	}
	
	// resets the position encoders on lift motors
	// should be called once only, during power up  (when lift is in base state)
	public static void resetPos()
	{		
		// reset upper lift motor encoder pulses to zero
		upperLiftMotor.setSelectedSensorPosition(0, PIDLOOP_IDX, TIMEOUT_MS);
	}	 	

    // slave motor configuration
    private static TalonSRX configureMotor(int talonID, boolean revMotor, int talonIDToFollow)
    {
    	TalonSRX _talon;
    	_talon = new TalonSRX(talonID);
    	_talon.setInverted(revMotor);
    	
    	if (talonIDToFollow > 0)
    		_talon.set(ControlMode.Follower, (double)talonIDToFollow);
    	
    	_talon.setNeutralMode(NeutralMode.Brake);
   	
    	return _talon;
    }
    
    // master motor configuration
    private static TalonSRX configureMotor(int talonID, boolean revMotor)
    {
		TalonSRX _talon;
		_talon = new TalonSRX(talonID);
		_talon.setInverted(revMotor);
		
		// forward limit switch is for up motion
		_talon.configForwardLimitSwitchSource(LimitSwitchSource.FeedbackConnector, LimitSwitchNormal.NormallyOpen, 0);
		// reverse limit switch is for down action
		_talon.configReverseLimitSwitchSource(LimitSwitchSource.FeedbackConnector, LimitSwitchNormal.NormallyOpen, 0);
				
		_talon.setNeutralMode(NeutralMode.Brake);
		
		return _talon;
    }

	/************************* lift brake & collector control functions **********************************/
	
	public static void liftBrakeOn()
	{
		if (liftBrakeOn)
			return;

		liftBrakeOn = true;
		brakeMotor.set(BRAKE_ON_STRENGTH);

		spawnBrakeMotorOffThread();  // turn brake motor off after a time period
		
		InputOutputComm.putBoolean(InputOutputComm.LogTable.kMainLog,"CubeMgmt/LiftBrake", liftBrakeOn);
		RPIComm.setBoolean("brakeOn", liftBrakeOn);
	}

	public static void liftBrakeOff()
	{
		if (!liftBrakeOn)
			return;

		liftBrakeOn = false;	
		brakeMotor.set(BRAKE_OFF_STRENGTH);  

		spawnBrakeMotorOffThread();   // turn brake motor off after a time period
		
		InputOutputComm.putBoolean(InputOutputComm.LogTable.kMainLog,"CubeMgmt/LiftBrake", liftBrakeOn);
		RPIComm.setBoolean("brakeOn", liftBrakeOn);
	}
	
	private static void spawnBrakeMotorOffThread()
	{
		// spawn a wait thread to ensure brake motor turned off only AFTER a certain period
		new Thread() {
			public void run() {
				try {
					Thread.sleep(500);    // wait half second before disengaging brake motor
					brakeMotor.set(0.0);   // turn off brake motor
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();		
	}

	public static void depositCube()
	{
		InputOutputComm.putDouble(InputOutputComm.LogTable.kMainLog,"CubeMgmt/CollectorLevel", COLLECTOR_OUT_AUTO_STRENGTH);
		leftCollectorMotor.set(COLLECTOR_OUT_AUTO_STRENGTH);
		rightCollectorMotor.set(COLLECTOR_OUT_AUTO_STRENGTH);
			
	}

	public static void collectCube()
	{
		InputOutputComm.putDouble(InputOutputComm.LogTable.kMainLog,"CubeMgmt/CollectorLevel", COLLECTOR_IN_AUTO_STRENGTH);
		leftCollectorMotor.set(COLLECTOR_IN_AUTO_STRENGTH);
		rightCollectorMotor.set(COLLECTOR_IN_AUTO_STRENGTH);
	}
	
	/************************* lift control functions **********************************/
		
	public static void runLift(double liftStrength)
	{
		InputOutputComm.putDouble(InputOutputComm.LogTable.kMainLog,"CubeMgmt/LiftStrength", liftStrength);
		upperLiftMotor.set(ControlMode.PercentOutput, liftStrength);		
	}
	
	/************************* UI input functions **********************************/
	
	private static void checkCollectorControls() {	
		double collectorMotorStrength = 0.0;
		
		// collector controls
		double collectorInStrength = gamepad.getRawAxis(HardwareIDs.COLLECTOR_IN_AXIS);
		double collectorOutStrength = gamepad.getRawAxis(HardwareIDs.COLLECTOR_OUT_AXIS);
		
		if ((collectorInStrength > DEAD_ZONE_THRESHOLD)  && (collectorInStrength < COLLECTOR_MAX_STRENGTH))
			collectorMotorStrength = collectorInStrength * COLLECTOR_IN_POLARITY;
		else if ((collectorOutStrength > DEAD_ZONE_THRESHOLD) && (collectorOutStrength < COLLECTOR_MAX_STRENGTH))
			collectorMotorStrength = collectorOutStrength * COLLECTOR_OUT_POLARITY;
		else
			collectorMotorStrength = COLLECTOR_AUTO_STRENGTH * COLLECTOR_IN_POLARITY;  // default auto motor rate
		
		InputOutputComm.putDouble(InputOutputComm.LogTable.kMainLog,"CubeMgmt/CollectorStrength", collectorMotorStrength);
		RPIComm.setDouble("collectorStrength", collectorMotorStrength);

		leftCollectorMotor.set(collectorMotorStrength);
		rightCollectorMotor.set(collectorMotorStrength);
	}
		
	private static void checkBrakeControls() {

		if (gamepad.getRawButton(HardwareIDs.BRAKE_TOGGLE_BUTTON)) {
			
			// only act if enough time has passed between brake control events
			if ((RobotController.getFPGATime() - brakeStartTimer) > BRAKE_LIMIT_USEC)
			{				
				if (liftBrakeOn)
					liftBrakeOff();
				else
					liftBrakeOn();
			
				// reset brake timer
				brakeStartTimer = RobotController.getFPGATime();				
			}
		}		
	}
	
	private static void checkLiftControls() {
		
		// cube lift control
		double liftStrength = gamepad.getRawAxis(HardwareIDs.LIFT_MOTOR_AXIS);
				
		// convert joystick value into motor speed value
		if (Math.abs(liftStrength) >= LIFT_MOTOR_DEAD_ZONE)
			if (liftStrength < 0)
				liftStrength *= LIFT_MOTOR_UP_FACTOR;
			else
				liftStrength *= LIFT_MOTOR_DOWN_FACTOR; 
		else 
			liftStrength = 0.0;
		
		// abort if lift brake is still on (don't fight the brake)
		//**** TURNED OFF: currently brake is not installed
		//if (liftBrakeOn)
		//	return;		
	
		// apply lift motor gain value
		runLift(liftStrength);
	}
			
	public static void autoInit() {				
		resetMotors();
		resetPos();
		liftBrakeOn();
	}

	public static void autoStop() {
		resetMotors();
	}
	
	public static void disabledInit() {		
		resetMotors();
	}
	
	public static void teleopInit() {
				
		resetMotors();
	}
	
	public static void teleopPeriodic() {
		
		checkCollectorControls();
		checkBrakeControls();
		checkLiftControls();
	}
		
}
