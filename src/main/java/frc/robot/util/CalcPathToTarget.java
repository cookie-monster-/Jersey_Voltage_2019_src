package frc.robot.util;
import java.text.DecimalFormat;

import frc.robot.Constants;
import jaci.pathfinder.Trajectory;
import jaci.pathfinder.Trajectory.Segment;

public class CalcPathToTarget {

    private Trajectory m_leftTrajectory;
    public Trajectory getLeftTrajectory(){
        return m_leftTrajectory;
    }
    private Trajectory m_rightTrajectory;
    public Trajectory getRightTrajectory(){
        return m_rightTrajectory;
    }

    private double m_xCenter, m_yCenter, m_xLeftCorner, m_yLeftCorner, m_xRightCorner, m_yRightCorner, m_hdg;
    private double m_arc1Radius, m_arc1Deg, m_arc2Radius, m_arc2Deg;
    private int m_currentIndex;

    VisionMath vm;
    AsyncAdHocLogger asyncAdHocLogger;
    public CalcPathToTarget(){
        asyncAdHocLogger = new AsyncAdHocLogger("");
        vm = new VisionMath();
        m_leftTrajectory = new Trajectory(new Segment[1000]);
        m_rightTrajectory = new Trajectory(new Segment[1000]);
        for(int i=0;i<m_leftTrajectory.length();i++){
            m_leftTrajectory.segments[i] = new Segment(0,0,0,0,0,0,0,0);
            m_rightTrajectory.segments[i] = new Segment(0,0,0,0,0,0,0,0);
        }
    }

    private void calcArc(double outerRadiusInches, double degrees, double startVel, double endVel){//+degrees = turn right, -degrees = turn left
        double desiredDist = ((2*Math.PI*outerRadiusInches*Math.abs(degrees))/360) / 12.0;//feet
        double currentPos = 0;
        double lastVel = startVel;
        asyncAdHocLogger.q("desiredDist: ").q(desiredDist).q(" outerRadiusInches: ").q(outerRadiusInches).q(" deg: ").q(degrees).go();
        
        while(currentPos<desiredDist){
            double currentVel,currentAcc;
            double timeToSlow = (endVel - lastVel) / Constants.kMaxAcceleration;
            double distToSlow = (endVel + lastVel) / 2.0 * timeToSlow;
            if(distToSlow >= (desiredDist - currentPos)){
                //deccel
                currentVel = lastVel - (Constants.kMaxAcceleration * Constants.kLooperDt);
                currentAcc = -Constants.kMaxAcceleration;
                if(currentVel <= endVel){
                    currentVel = endVel;
                    currentAcc = 0;
                }
            }else{
                //accel
                currentVel = lastVel + (Constants.kMaxAcceleration * Constants.kLooperDt);
                currentAcc = -Constants.kMaxAcceleration;
                if(currentVel >= Constants.kMaxFeetPerSecond){
                    currentVel = Constants.kMaxFeetPerSecond;
                    currentAcc = 0;
                }
            }
            double lastPos;
            if(m_currentIndex>0){
                lastPos = m_leftTrajectory.segments[m_currentIndex-1].position;
            }else{
                lastPos = 0;
            }
            currentPos = lastPos +(currentVel*Constants.kLooperDt);
            lastVel = currentVel;
            double hdg = (currentPos) / (outerRadiusInches);//radians

            if(degrees < 0){
                //turn left
                m_rightTrajectory.segments[m_currentIndex].position = currentPos;
                m_rightTrajectory.segments[m_currentIndex].velocity = currentVel;
                m_rightTrajectory.segments[m_currentIndex].acceleration = currentAcc;
                m_rightTrajectory.segments[m_currentIndex].heading = -hdg;
                m_rightTrajectory.segments[m_currentIndex].dt = Constants.kLooperDt;

                double ratio = (outerRadiusInches - (Constants.kWheelBaseFeet * 12)) / outerRadiusInches;
                m_leftTrajectory.segments[m_currentIndex].position = currentPos * ratio;
                m_leftTrajectory.segments[m_currentIndex].velocity = currentVel * ratio;
                m_leftTrajectory.segments[m_currentIndex].acceleration = currentAcc * ratio;
                m_leftTrajectory.segments[m_currentIndex].heading = -hdg;
                m_leftTrajectory.segments[m_currentIndex].dt = Constants.kLooperDt;
            }else{
                //turn right
                m_leftTrajectory.segments[m_currentIndex].position = currentPos;
                m_leftTrajectory.segments[m_currentIndex].velocity = currentVel;
                m_leftTrajectory.segments[m_currentIndex].acceleration = currentAcc;
                m_leftTrajectory.segments[m_currentIndex].heading = -hdg;
                m_leftTrajectory.segments[m_currentIndex].dt = Constants.kLooperDt;

                double ratio = (outerRadiusInches - (Constants.kWheelBaseFeet * 12)) / outerRadiusInches;
                m_rightTrajectory.segments[m_currentIndex].position = currentPos * ratio;
                m_rightTrajectory.segments[m_currentIndex].velocity = currentVel * ratio;
                m_rightTrajectory.segments[m_currentIndex].acceleration = currentAcc * ratio;
                m_rightTrajectory.segments[m_currentIndex].heading = -hdg;
                m_rightTrajectory.segments[m_currentIndex].dt = Constants.kLooperDt;
            }
            m_currentIndex++;
        }
    }

    private void calcPrepStage(){
        double actualRadius = Constants.kMinimumRadiusTurn * 12;//in
        
        double targetDist = (m_xCenter - (Constants.kVisionApproachDist*12)) / 2.0;
        double theta = Math.atan(m_yCenter / targetDist);
        double dashedLine = Math.sqrt((4*targetDist*targetDist)+((actualRadius-m_yCenter)*(actualRadius-m_yCenter)));
        if(actualRadius > 0.8*dashedLine){
            actualRadius = 0.75*dashedLine;
        }
        //first arc
        m_arc1Radius = actualRadius;
        theta -= m_hdg;
        theta *= 180.0 / Math.PI;
        m_arc1Deg = theta;
        updateRobotPos(m_arc1Radius, m_arc1Deg);

        //update position to end of first arc
        
    }

    private void updateRobotPos(double outerRadiusInches, double theta){
        double halfWheelBase = ((12* Constants.kWheelBaseFeet) / 2.0);//inches
        double leftRadius;
        double rightRadius;
        double centerRadius;
        if(theta>0){
            //was right turn
            leftRadius = outerRadiusInches;
            centerRadius = outerRadiusInches - halfWheelBase;
            rightRadius = outerRadiusInches - (halfWheelBase*2);
        }else{
            //was left turn
            rightRadius = outerRadiusInches;
            centerRadius = outerRadiusInches - halfWheelBase;
            leftRadius = outerRadiusInches - (halfWheelBase*2);
        }
        
        double xMultiplier = Math.sin(Math.abs(theta)*Math.PI/180.0);
        double yMultiplier = Math.cos(theta*Math.PI/180.0);
        double cosHdg = Math.cos(m_hdg);
        double sinHdg = Math.sin(m_hdg);
        double xCenterChangeRobotFrame = centerRadius * xMultiplier;
        double yCenterChangeRobotFrame = (centerRadius - centerRadius * yMultiplier) * Math.signum(theta);
        double xLeftChangeRobotFrame   = leftRadius   * xMultiplier;
        double yLeftChangeRobotFrame   = (leftRadius - leftRadius   * yMultiplier) * Math.signum(theta);
        double xRightChangeRobotFrame  = rightRadius  * xMultiplier;
        double yRightChangeRobotFrame  = (rightRadius - rightRadius  * yMultiplier) * Math.signum(theta);
        
        m_xCenter       += (xCenterChangeRobotFrame * cosHdg) - (yCenterChangeRobotFrame * sinHdg);
        m_yCenter       += (xCenterChangeRobotFrame * sinHdg) + (yCenterChangeRobotFrame * cosHdg);
        m_xLeftCorner   += (xLeftChangeRobotFrame   * cosHdg) - (yLeftChangeRobotFrame   * sinHdg);
        m_yLeftCorner   += (xLeftChangeRobotFrame   * sinHdg) + (yLeftChangeRobotFrame   * cosHdg);
        m_xRightCorner  += (xRightChangeRobotFrame  * cosHdg) - (yRightChangeRobotFrame  * sinHdg);
        m_yRightCorner  += (xRightChangeRobotFrame  * sinHdg) + (yRightChangeRobotFrame  * cosHdg);
        m_hdg           += theta*Math.PI/180.0;
    }

    private void calcFinalStage(){
        double xGoal = Constants.kVisionApproachDist*12;//convert ft to in
        double yGoal = 0;
        if(Math.abs(m_hdg*180.0/Math.PI) < 0.1){
            //don't divide by 0
            return;
        }
        double radiusIntercept = m_yLeftCorner + ((xGoal - m_xLeftCorner)*(m_yRightCorner - m_yLeftCorner)) / (m_xRightCorner - m_xLeftCorner);
        double radiusDist = Math.sqrt((m_xCenter - xGoal)*(m_xCenter - xGoal) + (m_yCenter - radiusIntercept) * (m_yCenter - radiusIntercept));

        double realRadius = (radiusDist + radiusIntercept) / 2.0;

        m_arc2Radius = realRadius + Constants.kWheelBaseFeet*12.0/2.0;
        asyncAdHocLogger.q("xCenter: ").q(m_xCenter).q(" yCenter: ").q(m_yCenter).go();
        asyncAdHocLogger.q("radiusIntercept: ").q(radiusIntercept).q(" radiusDist: ").q(radiusDist).go();
        asyncAdHocLogger.q("arc2rad: ").q(m_arc2Radius).q(" xTerm: ").q(Math.abs(m_xRightCorner - xGoal)).q(" hdg: ").q(m_hdg).q(" yCorn: ").q(m_yRightCorner).go();
        
        m_arc2Deg = -(m_hdg*180.0/Math.PI);

        updateRobotPos(m_arc2Radius, m_arc2Deg);
    }
    
    public void calcPath(double driveStartVel){
        vm.findRobotPos();
        double x = -70.0;//vm.getRobotX();
        double y = 10.0;//vm.getRobotY();

        double halfWheelBase = ((12* Constants.kWheelBaseFeet) / 2.0);//inches

        m_hdg = 10*Math.PI/180.0;//Gyro.getYaw() * Math.PI / 180.0;
        m_xCenter =-70;// x + (Constants.kCamToBumper * Math.cos(m_hdg));
        m_yCenter =10;// y - (Constants.kCamToBumper * Math.sin(m_hdg));
        double multiplier = 1;//m_hdg == 0 ? 1 : Math.signum(m_hdg);
		m_xRightCorner =    m_xCenter - multiplier * (halfWheelBase*Math.sin(m_hdg));
        m_xLeftCorner =     m_xCenter + multiplier * (halfWheelBase*Math.sin(m_hdg));
        m_yRightCorner =    m_yCenter + multiplier * ((halfWheelBase*Math.cos(m_hdg)));
        m_yLeftCorner =     m_yCenter - multiplier * ((halfWheelBase*Math.cos(m_hdg)));
        asyncAdHocLogger.q("START POS").go();
        asyncAdHocLogger.q("xLeft: ").q(m_xLeftCorner).q(" yLeft: ").q(m_yLeftCorner).q(" xCenter: ").q(m_xCenter).q(" yCenter: ").q(m_yCenter).q(" xRight: ").q(m_xRightCorner).q(" yRight: ").q(m_yRightCorner).go();

        double criticalHdg = Math.signum(m_yCenter) * -1.0 * Math.abs(Math.atan(Math.abs(m_yCenter) / (Math.abs(m_xCenter) - Constants.kVisionApproachDist)));//radians
		if(criticalHdg > 0){
            if(m_hdg < criticalHdg){
                //need 2 stages
                calcPrepStage();
                asyncAdHocLogger.q("AFTER PREP ARC").go();
                asyncAdHocLogger.q("xLeft: ").q(m_xLeftCorner).q(" yLeft: ").q(m_yLeftCorner).q(" xCenter: ").q(m_xCenter).q(" yCenter: ").q(m_yCenter).q(" xRight: ").q(m_xRightCorner).q(" yRight: ").q(m_yRightCorner).go();
                //calcArc(m_arc1Radius, m_arc1Deg, driveStartVel, Constants.kVisionMidPtVel);
            }else{
                //1 stage
                m_arc1Radius = -999;
            }
        }else{
            if(m_hdg > criticalHdg){
                //need 2 stages
                calcPrepStage();
                asyncAdHocLogger.q("AFTER PREP ARC").go();
                asyncAdHocLogger.q("xLeft: ").q(m_xLeftCorner).q(" yLeft: ").q(m_yLeftCorner).q(" xCenter: ").q(m_xCenter).q(" yCenter: ").q(m_yCenter).q(" xRight: ").q(m_xRightCorner).q(" yRight: ").q(m_yRightCorner).go();
                //calcArc(m_arc1Radius, m_arc1Deg, driveStartVel, Constants.kVisionMidPtVel);
            }else{
                //1 stage
                m_arc1Radius = -999;
            }
        }
        calcFinalStage();
        double startVel;
        if(m_arc1Radius < 0){
            //1 stage
            startVel = driveStartVel;
        }else{
            //2 stages
            startVel = (Constants.kVisionMidPtVel*((m_arc1Radius-(Constants.kWheelBaseFeet*12)) / m_arc1Radius));
        }
        asyncAdHocLogger.q("arc1Radius: ").q(m_arc1Radius).q(" arc1Deg: ").q(m_arc1Deg).q(" arc2Radius: ").q(m_arc2Radius).q(" arc2Deg: ").q(m_arc2Deg).go();
        asyncAdHocLogger.q("xCenter: ").q(m_xCenter).q(" yCenter: ").q(m_yCenter).go();
        //calcArc(m_arc2Radius, m_arc2Deg, startVel, Constants.kVisionApproachVel);
        asyncAdHocLogger.q("AFTER FINAL ARC").go();
        asyncAdHocLogger.q("xLeft: ").q(m_xLeftCorner).q(" yLeft: ").q(m_yLeftCorner).q(" xCenter: ").q(m_xCenter).q(" yCenter: ").q(m_yCenter).q(" xRight: ").q(m_xRightCorner).q(" yRight: ").q(m_yRightCorner).go();

        
        DecimalFormat df = new DecimalFormat("#.##");
        asyncAdHocLogger.q("LEFT PATH").go();
        for(int i=0;i<m_leftTrajectory.length();i++){
            Segment s = m_leftTrajectory.segments[i];
            if(s.dt != 0.0){
                asyncAdHocLogger.q("x: ").q(df.format(s.position)).q(" v: ").q(df.format(s.velocity)).q(" a: ").q(df.format(s.acceleration)).q(" yaw: ").q(df.format(s.heading)).go();
            }
        }
        asyncAdHocLogger.q("RIGHT PATH").go();
        for(int i=0;i<m_rightTrajectory.length();i++){
            Segment s = m_rightTrajectory.segments[i];
            if(s.dt != 0.0){
                asyncAdHocLogger.q("x: ").q(df.format(s.position)).q(" v: ").q(df.format(s.velocity)).q(" a: ").q(df.format(s.acceleration)).q(" yaw: ").q(df.format(s.heading)).go();
            }
        }
        

    }



}
