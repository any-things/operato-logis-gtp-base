select
  distinct domain_id, batch_id, job_type, sub_equip_cd as cell_cd, com_cd, class_cd
from
  orders
where
  domain_id = :domainId
  and batch_id = :batchId
  and class_cd not in (select class_cd from orders where domain_id = :domainId and batch_id = :mainBatchId)