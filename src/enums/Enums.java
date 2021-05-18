package enums;

public class Enums {
	
	public static final int MAX_CONSECUTIVE_PASSES = 20; // 6;
	
    public enum AllianceType
    {
        ADVERSARIAL,
        ALLIED,
        NEUTRAL;
    };
    
    public enum VisionType
    {
    	FULL,
    	UNAWARE,
    	PARTIALLY_AWARE
    }
}
