SELECT NVL(MAX(job_batch_seq), 0)
FROM   job_batch
WHERE  domain_id      = :domainId
AND    batch_group_id = :batchGroupId
