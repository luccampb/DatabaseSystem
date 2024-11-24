import java.time.LocalDateTime;

//Do not edit this file (it must not be submitted to Tabula)

public class ActPerformanceDetails {
    private int actID;
    private int fee;
    private LocalDateTime onTime;
    private int duration;

    public ActPerformanceDetails(int actID, int fee, LocalDateTime onTime, int duration){
        this.actID = actID;
        this.fee = fee;
        this.onTime = onTime;
        this.duration = duration;
    }

    public int getActID(){
        return this.actID;
    }

    public int getFee(){
        return this.fee;
    }

    public LocalDateTime getOnTime(){
        return this.onTime;
    }

    public int getDuration(){
        return duration;
    }
}