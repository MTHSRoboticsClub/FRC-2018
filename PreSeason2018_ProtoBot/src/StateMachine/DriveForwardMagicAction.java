package StateMachine;

import java.util.prefs.Preferences;

import NetworkComm.InputOutputComm;
import Systems.AutoDriveAssembly;

public class DriveForwardMagicAction extends Action {
	
	private String name;
	//private double targetPosRevs = 0.0;
	private double targetPosInches = 0.0;
	private int speedRpm = 0;
	private int accelRpm = 0;
	private boolean resetGyro = false;
	private double headingDeg = 0.0;   // angle to use if gyro not reset
		
	public DriveForwardMagicAction(double targetPosInches, int speedRpm, int accelRpm, boolean resetGyro, double headingDeg)
	{
		this.name = "<Drive Forward Magic Action>";		
		//this.targetPosRevs = targetPosInches/AutoDriveAssembly.INCHES_PER_REV;
		this.targetPosInches = targetPosInches;
		this.speedRpm = speedRpm;
		this.accelRpm = accelRpm;
		this.resetGyro = resetGyro;
		this.headingDeg = headingDeg;

		AutoDriveAssembly.initialize();
	}
	
	public DriveForwardMagicAction(String name, double targetPosInches, int speedRpm, int accelRpm, boolean resetGyro, double headingDeg)
	{
		this.name =  name;
		//this.targetPosRevs = targetPosInches/AutoDriveAssembly.INCHES_PER_REV;
		this.targetPosInches = targetPosInches;
		this.speedRpm = speedRpm;
		this.accelRpm = accelRpm;
		this.resetGyro = resetGyro;
		this.headingDeg = headingDeg;
				
		AutoDriveAssembly.initialize();
	}
		
	// action entry
	public void initialize() {
		// do some drivey initialization
		
		AutoDriveAssembly.autoInit(resetGyro, headingDeg, true);
		AutoDriveAssembly.autoMagicStraight(targetPosInches, speedRpm, accelRpm);
		
		super.initialize();
	}
	
	// called periodically
	public void process()  {
		
		// do some drivey stuff
						
		super.process();
	}
	
	// state cleanup and exit
	public void cleanup() {
		// do some drivey cleanup
					
		AutoDriveAssembly.autoStop();
		
		// cleanup base class
		super.cleanup();
	}
	
	public void persistWrite(int counter, Preferences prefs) {

		// create node for action
		Preferences actionPrefs = prefs.node(counter + "_" + this.name);
	
		// store action details
		actionPrefs.put("class",this.getClass().toString());
		actionPrefs.putDouble("speedRpm",this.speedRpm);
	}

}
