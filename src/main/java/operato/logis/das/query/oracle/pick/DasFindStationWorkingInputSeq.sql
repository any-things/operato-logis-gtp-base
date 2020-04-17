select 
	input_seq 
from (
	select 
		input_seq 
	from 
		job_inputs 
	where 
		domain_id = :domainId
		#if($id)
		and id = :id
		#end
		#if($batchId)
		and batch_id = :batchId
		#end
		#if($stationCd)
		and station_cd = :stationCd
		#end
		#if($status)
		and status = :status
		#end 
	order by 
		input_seq asc) 
where 
	rownum <= 1