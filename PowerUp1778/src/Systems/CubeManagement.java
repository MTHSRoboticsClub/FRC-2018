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
import edu.wpi.first.wpilibj.Compressor;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.Relay;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.Spark;
import edu.wpi.first.wpilibj.Timer;

public class CubeManagement {
	
	private static boolean initialized = false;
	private static final int TIMEOUT_MS = 0;  // set to zero if skipping confirmation
	private static final int PIDLOOP_IDX = 0;  // set to zero if primary loop
	private static final int PROFILE_SLOT = 0;

	// auto lift levels
	public static final int BASE_LEVEL = 0;
	public static final int SWITCH_LEVEL = 1;
	public static final int SCALE_LEVEL = 2;
	
	public static final int liftLevelPulses[] = {30, 140, 280};  // number of encoder pulses for each level  {base, lower, upper}
	private static final int speedRpm = 900;
	private static final int accelRpm = 450;
	
	// motor polarity
	public static final boolean RIGHT_COLLECTOR_REVERSE_MOTOR = false; 
	public static final boolean LEFT_COLLECTOR_REVERSE_MOTOR = false; 
			
	// grayhill encoder polarity
	public static final boolean ALIGNED_SENSOR = true; 

	// PID coeffs
	private static final double kP = 1.0;
	private static final double kI = 0.0;
	private static final double kD = 0.0;
	private static final double kF = 0.0;
	
	// collector strength (%VBus - max is 1.0)
	private static final double COLLECTOR_IN_STRENGTH = 0.75;
	private static final double COLLECTOR_OUT_STRENGTH = -1.00;
	private static final boolean LEFT_COLLECTOR_INVERTED = true;
	private static final boolean RIGHT_COLLECTOR_INVERTED = false;

	// teleop lift strength (%VBus - max is 1.0)
	private static final double LIFT_MOTOR_FACTOR = 0.25;
	private static final double LIFT_MOTOR_DEAD_ZONE = 0.1;
		
	// brake motor strength (%VBus - max is 1.0)
	private static final double BRAKE_ON_STRENGTH = 0.5;
	private static final double BRAKE_OFF_STRENGTH = -0.5;
	
	// control dead zone threshold
	private static final double DEAD_ZONE_THRESHOLD = 0.05;
	
	// idle time beyond which lift brake is applied
	private static final double LIFT_IDLE_BRAKE_SECONDS = 0.25;
	private static double liftIdleTimerStart = 0;
		    					
	// collector intake motors
	private static Spark leftCollectorMotor, rightCollectorMotor; 
	
	// lift motors
	private static TalonSRX upperLiftMotor, lowerLiftMotor;
	private static final boolean UPPER_REVERSE_MOTOR = false;
	private static final boolean LOWER_REVERSE_MOTOR = false;
	
	// flipper
	private static Spark flipperRelay;
	private static final boolean FLIPPER_RELAY_INVERTED = false;
	private static boolean flipperDeployed = false;
	private static double flipperStartTimer = 0;
	private static final double FLIPPER_LIMIT_USEC = 500000;
		
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
		flipperStartTimer = 0;
		
		// create and initialize collector motors (open-loop)
		leftCollectorMotor = new Spark(HardwareIDs.LEFT_COLLECTOR_PWM_ID);
		leftCollectorMotor.setInverted(LEFT_COLLECTOR_INVERTED);
		rightCollectorMotor = new Spark(HardwareIDs.RIGHT_COLLECTOR_PWM_ID);
		rightCollectorMotor.setInverted(RIGHT_COLLECTOR_INVERTED);

		// create and initialize flipper relay
        flipperRelay = new Spark(HardwareIDs.FLIPPER_RELAY_PWM_ID);
        flipperRelay.setInverted(FLIPPER_RELAY_INVERTED);
        
		// create and initialize brake motor (open-loop)
		brakeMotor = new Spark(HardwareIDs.BRAKE_MOTOR_PWM_ID);
		brakeMotor.setInverted(BRAKE_MOTOR_INVERTED);
		
		// create and initialize upper lift motor (closed-loop)
		upperLiftMotor = configureMotor(HardwareIDs.UPPER_LIFT_TALON_ID, UPPER_REVERSE_MOTOR, ALIGNED_SENSOR, kP, kI, kD, kF);

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
		// turn off collector motors
		leftCollectorMotor.set(0);
		rightCollectorMotor.set(0);	
		
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

    // open-loop motor configuration (and possibly follower)
    private static TalonSRX configureMotor(int talonID, boolean revMotor, int talonIDToFollow)
    {
    	TalonSRX _talon;
    	_talon = new TalonSRX(talonID);
    	_talon.setInverted(revMotor);
    	
    	if (talonIDToFollow > 0)
    		_talon.set(ControlMode.Follower, (double)talonIDToFollow);
    	
    	//_talon.setNeutralMode(NeutralMode.Brake);
   	
    	return _talon;
    }
    
    // closed-loop motor configuration
    private static TalonSRX configureMotor(int talonID, boolean revMotor, boolean alignSensor,
    									double pCoeff, double iCoeff, double dCoeff, double fCoeff)
    {
    	TalonSRX _talon;
    	_talon = new TalonSRX(talonID);
    	_talon.setInverted(revMotor);
    	
    	// set up sensor
    	_talon.configSelectedFeedbackSensor(FeedbackDevice.QuadEncoder, PIDLOOP_IDX, TIMEOUT_MS);
    	_talon.setSensorPhase(alignSensor); 
    	
    	// set up limit switches
		_talon.configForwardLimitSwitchSource(LimitSwitchSource.FeedbackConnector, LimitSwitchNormal.NormallyOpen, 0);
		_talon.configReverseLimitSwitchSource(LimitSwitchSource.FeedbackConnector, LimitSwitchNormal.NormallyOpen, 0);
   	
    	// set up closed loop control
    	_talon.selectProfileSlot(PROFILE_SLOT, PIDLOOP_IDX);
    	_talon.config_kP(PROFILE_SLOT, pCoeff, TIMEOUT_MS);
    	_talon.config_kI(PROFILE_SLOT, iCoeff, TIMEOUT_MS);
    	_talon.config_kD(PROFILE_SLOT, dCoeff, TIMEOUT_MS);
    	_talon.config_kF(PROFILE_SLOT, fCoeff, TIMEOUT_MS);
    	_talon.configMotionCruiseVelocity(0, TIMEOUT_MS);
    	_talon.configMotionAcceleration(0, TIMEOUT_MS);
    	_talon.setSelectedSensorPosition(0, PIDLOOP_IDX, TIMEOUT_MS);
 
    	_talon.setNeutralMode(NeutralMode.Brake);

    	return _talon;
    }
    
	
	/************************* flipper, lift brake & collector control functions **********************************/
	
	public static void flipperDeploy()
	{
		if (flipperDeployed)
			return;

		flipperDeployed = true;
		
		// actuate deployment relay
		flipperRelay.set(1.0);
		
		spawnFlipperRelayOffThread();  // turn flipper relay off after a time period
		
		InputOutputComm.putBoolean(InputOutputComm.LogTable.kMainLog,"CubeMgmt/Flipper", flipperDeployed);
	}
	
	private static void spawnFlipperRelayOffThread()
	{
		// spawn a wait thread to ensure flipper relay turned off only AFTER a certain period
		new Thread() {
			public void run() {
				try {
					Thread.sleep(1000);    // wait one second before disengaging flipper motor
					flipperRelay.set(0.0);   // turn off flipper relay
					flipperDeployed = false;
					InputOutputComm.putBoolean(InputOutputComm.LogTable.kMainLog,"CubeMgmt/Flipper", flipperDeployed);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();		
	}

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
		InputOutputComm.putDouble(InputOutputComm.LogTable.kMainLog,"CubeMgmt/CollectorLevel", COLLECTOR_OUT_STRENGTH);
		leftCollectorMotor.set(COLLECTOR_OUT_STRENGTH);
		rightCollectorMotor.set(COLLECTOR_OUT_STRENGTH);
			
	}

	public static void collectCube()
	{
		InputOutputComm.putDouble(InputOutputComm.LogTable.kMainLog,"CubeMgmt/CollectorLevel", COLLECTOR_IN_STRENGTH);
		leftCollectorMotor.set(COLLECTOR_IN_STRENGTH);
		rightCollectorMotor.set(COLLECTOR_IN_STRENGTH);
			
	}

	/************************* lift control functions **********************************/
		
	public static void goToTarget(int pulses)
	{		
		int targetPulses = pulses;

		String posStr = String.format("%d", targetPulses);
		InputOutputComm.putString(InputOutputComm.LogTable.kMainLog,"Auto/liftTargetPulses", posStr);
		
        // configure upper lift motor (lower lift motor follows)
		//configureMotionMagic(upperLiftMotor, targetPulses);
	}
	
	private static void configureMotionMagic(TalonSRX _talon, int targetPulses)
	{
		int nativeUnitsPer100ms = (int) ((double)speedRpm * HardwareIDs.RPM_TO_UNIT_PER_100MS);
		int accelNativeUnits = (int) ((double)accelRpm * HardwareIDs.RPM_TO_UNIT_PER_100MS);

		_talon.configMotionCruiseVelocity(nativeUnitsPer100ms, TIMEOUT_MS);
		_talon.configMotionAcceleration(accelNativeUnits, TIMEOUT_MS);
		_talon.set(ControlMode.MotionMagic, targetPulses);
	}
	
	/************************* UI input functions **********************************/
	
	private static void checkCollectorControls() {			
		// collector control
		double collectorStrength = gamepad.getRawAxis(HardwareIDs.COLLECTOR_IN_AXIS);
		if (Math.abs(collectorStrength) > DEAD_ZONE_THRESHOLD)
			collectorStrength = COLLECTOR_IN_STRENGTH;
		else if (gamepad.getRawButton(HardwareIDs.COLLECTOR_OUT_BUTTON))
			collectorStrength = COLLECTOR_OUT_STRENGTH;
		else
			collectorStrength = 0.0;
		
		InputOutputComm.putDouble(InputOutputComm.LogTable.kMainLog,"CubeMgmt/CollectorStrength", collectorStrength);
		RPIComm.setDouble("collectorStrength", collectorStrength);

		leftCollectorMotor.set(collectorStrength);
		rightCollectorMotor.set(collectorStrength);
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
			liftStrength *= LIFT_MOTOR_FACTOR; 
		else 
			liftStrength = 0.0;
		
		// abort if lift brake is still on (don't fight the brake)
		if (liftBrakeOn)
			return;		
	
		// apply lift motor gain value
		InputOutputComm.putDouble(InputOutputComm.LogTable.kMainLog,"CubeMgmt/LiftStrength", liftStrength);
		upperLiftMotor.set(ControlMode.PercentOutput, liftStrength);
	}
	
	private static void checkFlipperControls() {
	
		if (gamepad.getRawButton(HardwareIDs.FLIPPER_DEPLOY_BUTTON))
		{			
			// only act if enough time has passed between flipper control events
			if ((RobotController.getFPGATime() - flipperStartTimer) > FLIPPER_LIMIT_USEC)
			{				
				if (!flipperDeployed)
					flipperDeploy();	
				
				// reset flipper timer
				flipperStartTimer = RobotController.getFPGATime();				
			}
		}
	}
		
	public static void autoInit() {				
		resetMotors();
		resetPos();
		liftBrakeOn();
		//flipperDeploy();  // may want to wait on this, after movement in auto
	}

	public static void autoStop() {
		resetMotors();
	}
	
	public static void disabledInit() {		
		resetMotors();
	}
	
	public static void teleopInit() {
				
		resetMotors();
				
		liftIdleTimerStart = RobotController.getFPGATime();      
	}
	
	public static void teleopPeriodic() {
		
		checkCollectorControls();
		checkBrakeControls();
		checkLiftControls();
		checkFlipperControls();
	}
	
	public static int getLiftPos() {
		
		// Encoders now read only raw encoder values - convert raw to inches directly
		int upperPulses = upperLiftMotor.getSelectedSensorPosition(0);
				
		String posStr = String.format("%d", upperPulses);
		InputOutputComm.putString(InputOutputComm.LogTable.kMainLog,"Auto/LiftUpper", posStr);
		
		return upperPulses;
	}
	
}
