package operato.logis.das.service.model;

/**
 * 반품 작업 서머리 정보
 * 
 * @author shortstop
 */
public class RtnJobInstancesSummary {
	/**
	 * 
	 */
	private Integer resultOrderQty;
	/**
	 * 실적 수량
	 */
	private Integer resultQty;
	/**
	 * 작업 진행율
	 */
	private Float progressRate;
	/**
	 * 작업 UPH
	 */
	private Float jobUph;
	
	public Integer getResultOrderQty() {
		return resultOrderQty;
	}
	
	public void setResultOrderQty(Integer resultOrderQty) {
		this.resultOrderQty = resultOrderQty;
	}
	
	public Float getProgressRate() {
		return progressRate;
	}
	
	public void setProgressRate(Float progressRate) {
		this.progressRate = progressRate;
	}
	
	public Float getJobUph() {
		return jobUph;
	}
	
	public void setJobUph(Float jobUph) {
		this.jobUph = jobUph;
	}
	
	public Integer getResultQty() {
		return resultQty;
	}
	
	public void setResultQty(Integer resultQty) {
		this.resultQty = resultQty;
	}
	
}
