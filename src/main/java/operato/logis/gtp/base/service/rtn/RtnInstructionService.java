package operato.logis.gtp.base.service.rtn;

import java.util.List;
import java.util.Map;

import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.service.api.IInstructionService; 

public class RtnInstructionService  implements IInstructionService{

	@Override
	public Map<String, Object> searchInstructionData(JobBatch batch, Object... params) {
		// TODO Auto-generated method stub
		return null;
	}
	
	/**
	 * 무오더 반품 유형이면 작업 지시 완료 
	 *
	 * @param batch
	 * @param rackList
	 * @param params
	 * @return
	 */
	@Override
	public int instructBatch(JobBatch batch, List<String> equipIdList, Object... params) {
		int instructCount = 0;

//		if(this.beforeInstructBatch(batch, equipIdList)) {
//			instructCount += this.doInstructBatch(batch, rackList);
//			//this.afterInstructBatch(batch, rackList);
//		}

		return instructCount;
	}

	@Override
	public int instructTotalpicking(JobBatch batch, List<String> equipIdList, Object... params) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int mergeBatch(JobBatch mainBatch, JobBatch newBatch, Object... params) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int cancelInstructionBatch(JobBatch batch) {
		// TODO Auto-generated method stub
		return 0;
	}

}
