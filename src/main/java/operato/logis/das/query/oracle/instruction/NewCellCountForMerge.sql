select count(class_cd) from (
  select distinct(class_cd) as class_cd from orders where domain_id = :domainId and batch_id = :mergeBatchId
  minus
  select distinct(class_cd) as class_cd from orders where domain_id = :domainId and batch_id = :batchId
)