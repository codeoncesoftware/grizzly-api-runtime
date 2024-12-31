package fr.codeonce.grizzly.runtime.service.feign;


public class JobCreatedApi {

    private String jobId;

    private Jobstatus jobstatus;

    public JobCreatedApi(String jobId) {
        this.jobId = jobId;

    }


    public JobCreatedApi(String jobId, Jobstatus jobstatus) {
        this.jobId = jobId;
        this.jobstatus = jobstatus;
    }

    @Override
    public String toString() {
        return "JobCreatedApi{" +
                "jobId='" + jobId + '\'' +
                ", jobstatus=" + jobstatus +
                '}';
    }
}
