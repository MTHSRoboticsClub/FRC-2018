#include "opencv2/features2d.hpp"
#include "opencv2/highgui/highgui.hpp"
#include "opencv2/core/core.hpp"
#include "opencv2/imgproc/imgproc.hpp"

#include <networktables/NetworkTable.h>
#include <networktables/NetworkTableInstance.h>


#include <libv4l2.h>
#include <linux/videodev2.h>
#include <sys/ioctl.h>
#include <fcntl.h>

#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <ctime>

using namespace cv;
using namespace std;
using namespace nt;

// color filter params
int minColor_h = 0;
int minColor_s = 0;
int minColor_v = 0;

int maxColor_h = 255;
int maxColor_s = 255;
int maxColor_v = 255;

double minArea = 100.0;
double maxArea = 30000.0;

int dilationFactor = 5;
float exposure = 0.0;
float maxExposure = 100.0;
bool autoCam = false;

int hue_min_slider, hue_max_slider;
int sat_min_slider, sat_max_slider;
int val_min_slider, val_max_slider;
int area_min_slider, area_max_slider;
int dilation_slider;
int exposure_slider;

// overlay variables - assuming capture of 160x120 dims

// guidelines for gear placement
const Point gearLineUpperCenter(65,100);
const Point gearLineUpperBottom(40,120);
const Point gearLineLowerCenter(70,100);
const Point gearLineLowerBottom(50,120);

// Near Goal
const Point highCenter(60,35);
const Point highSize(18,13);
const Point highUL(highCenter.x - (highSize.x/2), highCenter.y - (highSize.y/2));
const Point highLR(highCenter.x + (highSize.x/2), highCenter.y + (highSize.y/2));

// Medium Goal
const Point middleCenter(63,57);
const Point middleSize(14,11);
const Point middleUL(middleCenter.x - (middleSize.x/2), middleCenter.y - (middleSize.y/2));
const Point middleLR(middleCenter.x + (middleSize.x/2), middleCenter.y + (middleSize.y/2));

// Far Goal - Not Used
//const Point lowCenter(54,55);
//const Point lowSize(16,5);
//const Point lowUL(lowCenter.x - (lowSize.x/2), lowCenter.y - (lowSize.y/2));
//const Point lowLR(lowCenter.x + (lowSize.x/2), lowCenter.y + (lowSize.y/2));


// video capture objects
VideoCapture cap;
//VideoCapture cap(0);     // get cam 0
//VideoCapture cap(1);     // get cam 1
//VideoCapture cap("/dev/video1");    // not used

int descriptor = v4l2_open("/dev/video0",O_RDWR);   // v4l capture object
//int descriptor = v4l2_open("/dev/video1",O_RDWR);   // v4l capture object

void autoExposureOn()
{
	v4l2_control c;

	c.id = V4L2_CID_EXPOSURE_AUTO;
	c.value = 3;
	v4l2_ioctl(descriptor,VIDIOC_S_CTRL,&c);
}

void autoExposureOff()
{
	v4l2_control c;

	c.id = V4L2_CID_EXPOSURE_AUTO;
	c.value = 1;
	v4l2_ioctl(descriptor,VIDIOC_S_CTRL,&c);
}


void set_exposure(float exp_abs) 
{
	v4l2_control c;

	// set exposure absolute value
	c.id = V4L2_CID_EXPOSURE_ABSOLUTE;
	c.value = exp_abs;
	v4l2_ioctl(descriptor,VIDIOC_S_CTRL,&c);
}


void draw_overlay(Mat& inImg) 
{
		
	// draw gear post guidelines
	//line(inImg,gearLineUpperCenter,gearLineUpperBottom,Scalar(0,255,255), 1, 8);
	//line(inImg,gearLineLowerCenter,gearLineLowerBottom,Scalar(0,255,255), 1, 8);
	
	// draw boxes for boiler
	rectangle(inImg, highUL, highLR, Scalar(255, 0, 0), 1, 8, 0);					
	rectangle(inImg,middleUL,middleLR, Scalar(0, 255, 0), 1, 8, 0);					
	//rectangle(inImg,lowUL,lowLR, Scalar(0, 0, 255), 1, 8, 0);

}

int main()
{
	FILE *parameter_file = NULL;

	char roborio_ipaddr[32];

	/********* OpenCV static image section *******/
	Mat overlayImg;

	/********* MJPG streaming section *******/
	String outFile = "./out.mjpg";
	
	/******* timer section *****/
	clock_t startTime = clock();
	clock_t duration;
	double timeInSeconds;
	double resetVideoTimeSec = 60.0;  // Release/reset VideoWriter every so many seconds
	int resetCtr = 0;
	
	/********* OpenCV parameter section *******/
	int frameWidth =  640;
	int frameHeight = 480;
	int imageCenterX, imageCenterY;

	// target area
	int targetIndex = -1;
	bool targetDetected = false;

	/******** OpenCV parameter section *******/

	// read in parameters (if file exists)
	
	parameter_file = fopen("/home/pi/chillout/chillOut_params.txt","r");
	if (parameter_file != NULL) {
		printf("Reading parameters from file\n");
		fscanf(parameter_file,"roborio_ipaddr = %s\n",roborio_ipaddr);
		fscanf(parameter_file,"frameWidth = %d\n",&frameWidth);
		fscanf(parameter_file,"frameHeight = %d\n",&frameHeight);
		fscanf(parameter_file,"minArea = %f\n",&minArea);
		fscanf(parameter_file,"maxArea = %f\n",&maxArea);
		fscanf(parameter_file,"minColor_h = %d\n",&minColor_h);
		fscanf(parameter_file,"maxColor_h = %d\n",&maxColor_h);
		fscanf(parameter_file,"minColor_s = %d\n",&minColor_s);
		fscanf(parameter_file,"maxColor_s = %d\n",&maxColor_s);
		fscanf(parameter_file,"minColor_v = %d\n",&minColor_v);
		fscanf(parameter_file,"maxColor_v = %d\n",&maxColor_v);
		fscanf(parameter_file,"dilationFactor = %d\n",&dilationFactor);
		fscanf(parameter_file,"maxExposure = %f\n",&maxExposure);
	}
	fclose(parameter_file);
	printf("File read complete.\n");
	printf("roborio_ipaddr = %s\n",roborio_ipaddr);
	printf("frameWidth = %d\n",frameWidth);
	printf("frameHeight = %d\n",frameHeight);
	printf("minArea = %f\n",minArea);
	printf("maxArea = %f\n",maxArea);
	printf("minColor_h = %d\n",minColor_h);
	printf("maxColor_h = %d\n",maxColor_h);
	printf("minColor_s = %d\n",minColor_s);
	printf("maxColor_s = %d\n",maxColor_s);
	printf("minColor_v = %d\n",minColor_v);
	printf("maxColor_v = %d\n",maxColor_v);
	printf("dilationFactor = %d\n",dilationFactor);
	printf("maxExposure = %f\n",maxExposure);
	
	// calculate image center
	imageCenterX = frameWidth/2;
	imageCenterY = frameHeight/2;

	// read in the overlay image (depends on 160x120, nothing else is valid)
	/*
	if (frameWidth == 160)
		overlayImg = imread("/home/pi/chillout/Steamworks1778_Camera_Overlay1_160x120.png",CV_LOAD_IMAGE_COLOR);
	else
		overlayImg = Mat::zeros(Size(frameWidth, frameHeight),CV_8UC3);
	*/

	// initialize network table for comm with the robot
	auto tableInstance = nt::NetworkTableInstance::Create();
	tableInstance.SetServer("Roborio-1778-frc.local",8081);
	auto table = tableInstance.GetTable("RPIComm/Data_Table");
	
	/*
	static std::shared_ptr<NetworkTable> table;
	NetworkTable::SetIPAddress(roborio_ipaddr);
	NetworkTable::SetClientMode();
	NetworkTable::Initialize();
	table = NetworkTable::GetTable("RPIComm/Data_Table");
	*/
	
	// DEBUG ONLY - enable auto exposure for all modes
	//autoExposureOn();

	// disable auto exposure for all modes
	// (this might be faster FPS)
	autoExposureOff();
	exposure = maxExposure;
	set_exposure(exposure);

	autoCam = false;
	table->GetEntry("autoCam").SetDefaultBoolean(false);
		
	// debug only - auto
	//set_exposure(0);
	
	// Keep trying until camera open
    cap = VideoCapture(0);
    int i = 0;
    while (!cap.isOpened()) {
		printf("Retrying VideoCapture open..(%d)\n",i++);
		cap.release();
		usleep(2e6);
		cap = VideoCapture(0);
	}
	printf("VideoCapture device open!\n");

    // initialize frame size
    if (cap.isOpened()) {
		cap.set(CV_CAP_PROP_FRAME_WIDTH,frameWidth);
		cap.set(CV_CAP_PROP_FRAME_HEIGHT,frameHeight);
    }

    // capture loop - runs forever
    while( cap.isOpened() )
    {
        Mat inputImg, src_gray, hsvImg, binaryImg;
		Mat erosionImg, dilationImg, contourImg;
		Mat outputImg;

		vector<Vec4i> hierarchy;
		vector<vector<Point>> contours;

		// check if autoCam boolean has changed
		bool tableAutoCam = table->GetEntry("autoCam").GetBoolean(false);
		if (autoCam != tableAutoCam) 
		{
			autoCam = tableAutoCam;
			if (autoCam)
				exposure = 0;
			else
				exposure = maxExposure;

			printf("setting exposure to %f\n",exposure);
			set_exposure(exposure);
		}

		// capture latest image from camera
			if ( ! cap.read(inputImg) )
				break;
			
		// color threshold input image into binary image
			cvtColor( inputImg, hsvImg, CV_BGR2HSV );
		inRange(hsvImg, Scalar(minColor_h, minColor_s, minColor_v), Scalar(maxColor_h, maxColor_s, maxColor_v), binaryImg);		/*green*/

		Mat binary2 = binaryImg.clone();

		// erode thresholded image - not used
		//Mat erosionElement = getStructuringElement(MORPH_RECT, Size(7,7), Point(3,3));
		//erode(binaryImg,erosionImg,erosionElement);	
		
		// dilate image (unify pieces)
		int dil = dilationFactor;
		int dil2 = dilationFactor*2 + 1;
		Mat dilateElement = getStructuringElement(MORPH_RECT, Size(dil2,dil2), Point(dil,dil));
		//dilate(erosionImg, dilationImg, dilateElement);
		dilate(binary2, dilationImg, dilateElement);

		// find contours from dilated image, place in list (vector)
		findContours(dilationImg, contours, hierarchy, CV_RETR_TREE, CV_CHAIN_APPROX_SIMPLE,Point(0,0));
		//findContours(binary2, contours, hierarchy, CV_RETR_TREE, CV_CHAIN_APPROX_SIMPLE,Point(0,0));

		// find the convex hull objects
		
		vector<vector<Point>> hulls (contours.size());	
		for (int i=0; i< contours.size(); i++)
		{
			convexHull(Mat(contours[i]), hulls[i], false);
		}

		// create stats for each convex hull	
		vector<Moments>mu(hulls.size());	  // hull moments
		vector<Point2f>mc(hulls.size());       // hull mass centers
		vector<double>targetArea(hulls.size());   // hull areas

		for (int i=0; i<hulls.size(); i++)
		{
			mu[i] = moments(hulls[i], false);   // find moments
			mc[i] = Point2f(mu[i].m10/mu[i].m00, mu[i].m01/mu[i].m00);
			targetArea[i] = contourArea(hulls[i]);
		}

		int maxTargetArea = -1;
		int targetIndex = -1;
		targetDetected = false;
		for (int i=0; i<hulls.size(); i++)
		{
			// see if this target meets the minimum area requirement
			if (targetArea[i] > minArea)
			{
				targetDetected = true;

				// see if this target is the biggest so far
				if (targetArea[i] > maxTargetArea)
				{
					targetIndex = i;
					maxTargetArea = targetArea[i];
				}
			}
		}

		// create contour image
		contourImg = Mat::zeros(binaryImg.size(),CV_8UC3);
		for (int i=0; i< contours.size(); i++)
		{
			Scalar colorGreen = Scalar(0, 0, 255);  // green
			Scalar colorWhite = Scalar(255, 255, 255);  // white
			drawContours(contourImg, hulls, i, colorWhite, 2, 8, hierarchy, 0, Point());
		}

		// if in auto targeting and target meets criteria, do stuff
		if (autoCam) 
		{
			if (targetDetected)
			{
				// write target info out to roborio
				table->GetEntry("targets").SetDouble(1.0f);
				//table->GetEntry("targetX").SetDouble(mc[targetIndex].x - imageCenterX);
				//table->GetEntry("targetY").SetDouble(mc[targetIndex].y - imageCenterY);
				table->GetEntry("targetX").SetDouble(mc[targetIndex].x);
				table->GetEntry("targetY").SetDouble(mc[targetIndex].y);
				table->GetEntry("targetArea").SetDouble(targetArea[targetIndex]);
				table->GetEntry("frameWidth").SetDouble((float)frameWidth);
				table->GetEntry("frameHeight").SetDouble((float)frameHeight);

				// draw the target on one of the images
				Scalar colorWhite = Scalar(255, 255, 255);  // white
				Scalar colorGreen = Scalar(0, 255, 0);  // green
				Scalar colorBlue = Scalar(255, 0, 0);  // blue
				drawContours(inputImg, hulls, targetIndex, colorGreen, 2, 8, hierarchy, 0, Point());
				circle(inputImg, mc[targetIndex], 3 ,colorBlue,2,6,0);

				//printf("Target area %3.0f detected at (%3.0f,%3.0f)\n",
				//	targetArea[targetIndex], mc[targetIndex].x - imageCenterX,
				//			     mc[targetIndex].y - imageCenterY);
			}
			else
			{
				// let roborio know that no target is detected
				table->GetEntry("targets").SetDouble(0.0f);
				//printf("No target\n");
			}
		}

		// create output image with overlay
		//float alpha = 0.6;
		//float beta = 1.0 - alpha;
		//addWeighted(inputImg, alpha, overlayImg, beta, 0.0, outputImg);

		outputImg = inputImg;
		draw_overlay(outputImg);
		
		/*** Image output code - debug only ****/
		//imshow("original",inputImg);
		//imshow("overlay",overlayImg);

		// first stage: threshold image display
		imshow("threshold binary",binaryImg);
		moveWindow("threshold binary",0,0);
		
		// second stage: contour image display
		imshow("contours",contourImg);
		moveWindow("contours",0,200);

		// third stage: output target
		imshow("output",outputImg);
		moveWindow("output",0, 400);

		// write out to file (for webserver)
		
		int k = waitKey(1);
		if ( k==27 )
			break;
	}
	return 0;
}
