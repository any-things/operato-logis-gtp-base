package operato.logis.das.service.model;


/**
 * 주문 그룹 정보
 * 
 * @author shortstop
 */
public class OrderGroup {

	private String classCd;
	
	public OrderGroup() {
	}
	
	public OrderGroup(String classCd) {
		this.classCd = classCd;
	}

	public String getClassCd() {
		return classCd;
	}

	public void setClassCd(String classCd) {
		this.classCd = classCd;
	}
	
}
