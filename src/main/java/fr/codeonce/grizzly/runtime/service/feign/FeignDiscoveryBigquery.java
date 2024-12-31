package fr.codeonce.grizzly.runtime.service.feign;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;


@FeignClient(name = "grizzly-bigquery-runtime")
public interface FeignDiscoveryBigquery {

    @PostMapping("/bigquery/kafka/createJob")
    public JobCreatedApi executeJob(@RequestParam String containerId , @RequestParam String Query);


    @GetMapping("/bigquery/kafka/status/")
    public JobCreatedApi getJobStatus(@RequestParam String containerId , @RequestParam String jobId);

}
