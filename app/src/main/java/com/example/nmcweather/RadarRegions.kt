package com.example.nmcweather

/** 中央气象局雷达目录：区域拼图 + 按省分类的官方单站。 */
data class RadarItem(val id: String, val name: String, val url: String)
data class RadarProvince(val id: String, val name: String, val stations: List<RadarItem>)

object RadarRegions {
    private const val BASE = "https://www.nmc.cn"

    val regions: List<RadarItem> = listOf(
        RadarItem("region_chinaall", "全国", BASE + "/publish/radar/chinaall.html"),
        RadarItem("region_huabei", "华北", BASE + "/publish/radar/huabei.html"),
        RadarItem("region_dongbei", "东北", BASE + "/publish/radar/dongbei.html"),
        RadarItem("region_huadong", "华东", BASE + "/publish/radar/huadong.html"),
        RadarItem("region_huazhong", "华中", BASE + "/publish/radar/huazhong.html"),
        RadarItem("region_huanan", "华南", BASE + "/publish/radar/huanan.html"),
        RadarItem("region_xinan", "西南", BASE + "/publish/radar/xinan.html"),
        RadarItem("region_xibei", "西北", BASE + "/publish/radar/xibei.html")
    )

    val provinces: List<RadarProvince> = listOf(
        RadarProvince("beijing", "北京", listOf(
            RadarItem("station_beijing_da_xing", "大兴", BASE + "/publish/radar/bei-jing/da-xing.htm"),
            RadarItem("station_beijing_haituoshan", "海坨山", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/beijing/haituoshan/index.html")
        )),
        RadarProvince("tianjin", "天津", listOf(
            RadarItem("station_tianjin_tian_jin", "塘沽", BASE + "/publish/radar/tian-jin/tian-jin.htm"),
            RadarItem("station_tianjin_baodi", "宝坻", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/tianjin/baodi/index.html")
        )),
        RadarProvince("hebei", "河北", listOf(
            RadarItem("station_hebei_shi_jia_zhuang", "石家庄", BASE + "/publish/radar/he-bei/shi-jia-zhuang.htm"),
            RadarItem("station_hebei_zhang_jia_kou", "张家口", BASE + "/publish/radar/he-bei/zhang-jia-kou.htm"),
            RadarItem("station_hebei_cheng_de", "承德", BASE + "/publish/radar/he-bei/cheng-de.htm"),
            RadarItem("station_hebei_qin_huang_dao", "秦皇岛", BASE + "/publish/radar/he-bei/qin-huang-dao.htm"),
            RadarItem("station_hebei_cang_zhou", "沧州", BASE + "/publish/radar/he-bei/cang-zhou.htm"),
            RadarItem("station_hebei_han_dan", "邯郸", BASE + "/publish/radar/he-bei/han-dan.htm")
        )),
        RadarProvince("shanxi", "山西", listOf(
            RadarItem("station_shanxi_tai_yuan", "太原", BASE + "/publish/radar/shan-xi/tai-yuan.htm"),
            RadarItem("station_shanxi_lin_fen", "临汾", BASE + "/publish/radar/shan-xi/lin-fen.htm"),
            RadarItem("station_shanxi_da_tong", "大同", BASE + "/publish/radar/shan-xi/da-tong.htm"),
            RadarItem("station_shanxi_chang_zhi", "长治", BASE + "/publish/radar/shan-xi/chang-zhi.htm"),
            RadarItem("station_shanxi_lv_liang", "吕梁", BASE + "/publish/radar/shan-xi/lv-liang.htm"),
            RadarItem("station_shanxi_wuzhai", "五寨", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/shanxi/wuzhai/index.html")
        )),
        RadarProvince("neimenggu", "内蒙古", listOf(
            RadarItem("station_neimenggu_e_er_duo_si", "鄂尔多斯", BASE + "/publish/radar/nei-meng/e-er-duo-si.htm"),
            RadarItem("station_neimenggu_hu_he_hao_te", "呼和浩特", BASE + "/publish/radar/nei-meng/hu-he-hao-te.htm"),
            RadarItem("station_neimenggu_chi_feng", "赤峰", BASE + "/publish/radar/nei-meng/chi-feng.htm"),
            RadarItem("station_neimenggu_hai_la_er", "海拉尔", BASE + "/publish/radar/nei-meng/hai-la-er.htm"),
            RadarItem("station_neimenggu_tong_liao", "通辽", BASE + "/publish/radar/nei-meng/tong-liao.htm"),
            RadarItem("station_neimenggu_lin_he", "临河", BASE + "/publish/radar/nei-meng/lin-he.htm"),
            RadarItem("station_neimenggu_huo_lin_guo_le", "霍林郭勒", BASE + "/publish/radar/nei-meng/huo-lin-guo-le.htm"),
            RadarItem("station_neimenggu_man_zhou_li", "满洲里", BASE + "/publish/radar/nei-meng/man-zhou-li.htm"),
            RadarItem("station_neimenggu_jining", "集宁", BASE + "/publish/radar/nei-meng/jining.html"),
            RadarItem("station_neimenggu_xilinhaote", "锡林浩特", BASE + "/publish/radar/nei-meng/xilinhaote.html"),
            RadarItem("station_neimenggu_aershan", "阿尔山", BASE + "/publish/radar/nei-meng/aershan.html"),
            RadarItem("station_neimenggu_balinzuoqi", "巴林左旗", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/neimenggu/balinzuoqi/index.html")
        )),
        RadarProvince("liaoning", "辽宁", listOf(
            RadarItem("station_liaoning_shen_yang", "沈阳", BASE + "/publish/radar/liao-ning/shen-yang.htm"),
            RadarItem("station_liaoning_ying_kou", "营口", BASE + "/publish/radar/liao-ning/ying-kou.htm"),
            RadarItem("station_liaoning_da_lian", "大连", BASE + "/publish/radar/liao-ning/da-lian.htm"),
            RadarItem("station_liaoning_chao_yang", "朝阳", BASE + "/publish/radar/liao-ning/chao-yang.htm"),
            RadarItem("station_liaoning_huludao", "葫芦岛", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/liaoning/huludao/index.html")
        )),
        RadarProvince("jilin", "吉林", listOf(
            RadarItem("station_jilin_chang_chun", "长春", BASE + "/publish/radar/ji-lin/chang-chun.htm"),
            RadarItem("station_jilin_bai_cheng", "白城", BASE + "/publish/radar/ji-lin/bai-cheng.htm"),
            RadarItem("station_jilin_bai_shan", "白山", BASE + "/publish/radar/ji-lin/bai-shan.htm"),
            RadarItem("station_jilin_liao_yuan", "辽源", BASE + "/publish/radar/ji-lin/liao-yuan.htm"),
            RadarItem("station_jilin_yan_ji", "延吉", BASE + "/publish/radar/ji-lin/yan-ji.htm"),
            RadarItem("station_jilin_songyuan", "松原", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/jilin/songyuan/index.html"),
            RadarItem("station_jilin_jilin", "吉林", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/jilin/jilin/index.html")
        )),
        RadarProvince("heilongjiang", "黑龙江", listOf(
            RadarItem("station_heilongjiang_ha_er_bin", "哈尔滨", BASE + "/publish/radar/hei-long-jiang/ha-er-bin.htm"),
            RadarItem("station_heilongjiang_qi_qi_ha_er", "齐齐哈尔", BASE + "/publish/radar/hei-long-jiang/qi-qi-ha-er.htm"),
            RadarItem("station_heilongjiang_jia_mu_si", "佳木斯", BASE + "/publish/radar/hei-long-jiang/jia-mu-si.htm"),
            RadarItem("station_heilongjiang_suihua", "绥化", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/heilongjiang/suihua/index.html"),
            RadarItem("station_heilongjiang_jia_ge_da_qi", "加格达奇", BASE + "/publish/radar/hei-long-jiang/jia-ge-da-qi.htm"),
            RadarItem("station_heilongjiang_hei_he", "黑河", BASE + "/publish/radar/hei-long-jiang/hei-he.htm"),
            RadarItem("station_heilongjiang_yi_chun", "伊春", BASE + "/publish/radar/hei-long-jiang/yi-chun.htm"),
            RadarItem("station_heilongjiang_baoqing", "宝清", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/heilongjiang/baoqing/index.html"),
            RadarItem("station_heilongjiang_mu_dan_jiang", "牡丹江", BASE + "/publish/radar/hei-long-jiang/mu-dan-jiang.htm"),
            RadarItem("station_heilongjiang_jian_san_jiang", "建三江", BASE + "/publish/radar/hei-long-jiang/jian-san-jiang.htm"),
            RadarItem("station_heilongjiang_jiu_san", "九三", BASE + "/publish/radar/hei-long-jiang/jiu-san.htm"),
            RadarItem("station_heilongjiang_hei_xia_zi_dao", "黑瞎子岛", BASE + "/publish/radar/hei-long-jiang/hei-xia-zi-dao.htm"),
            RadarItem("station_heilongjiang_daqing", "大庆", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/heilongjiang/daqing/index.html")
        )),
        RadarProvince("shanghai", "上海", listOf(
            RadarItem("station_shanghai_qing_pu", "青浦", BASE + "/publish/radar/shang-hai/qing-pu.htm"),
            RadarItem("station_shanghai_nan_hui", "南汇", BASE + "/publish/radar/shang-hai/nan-hui.htm")
        )),
        RadarProvince("jiangsu", "江苏", listOf(
            RadarItem("station_jiangsu_nan_jing", "南京", BASE + "/publish/radar/jiang-su/nan-jing.htm"),
            RadarItem("station_jiangsu_nan_tong", "南通", BASE + "/publish/radar/jiang-su/nan-tong.htm"),
            RadarItem("station_jiangsu_yan_cheng", "盐城", BASE + "/publish/radar/jiang-su/yan-cheng.htm"),
            RadarItem("station_jiangsu_xu_zhou", "徐州", BASE + "/publish/radar/jiang-su/xu-zhou.htm"),
            RadarItem("station_jiangsu_lian_yun_gang", "连云港", BASE + "/publish/radar/jiang-su/lian-yun-gang.htm"),
            RadarItem("station_jiangsu_chang_zhou", "常州", BASE + "/publish/radar/jiang-su/chang-zhou.htm"),
            RadarItem("station_jiangsu_huai_an", "淮安", BASE + "/publish/radar/jiang-su/huai-an.htm"),
            RadarItem("station_jiangsu_tai_zhou", "泰州", BASE + "/publish/radar/jiang-su/tai-zhou.htm"),
            RadarItem("station_jiangsu_suqian", "宿迁", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/jiangsu/suqian/index.html"),
            RadarItem("station_jiangsu_dafeng", "大丰", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/jiangsu/dafeng/index.html")
        )),
        RadarProvince("zhejiang", "浙江", listOf(
            RadarItem("station_zhejiang_hang_zhou", "杭州", BASE + "/publish/radar/zhe-jiang/hang-zhou.htm"),
            RadarItem("station_zhejiang_ning_bo", "宁波", BASE + "/publish/radar/zhe-jiang/ning-bo.htm"),
            RadarItem("station_zhejiang_wen_zhou", "温州", BASE + "/publish/radar/zhe-jiang/wen-zhou.htm"),
            RadarItem("station_zhejiang_zhou_shan", "舟山", BASE + "/publish/radar/zhe-jiang/zhou-shan.htm"),
            RadarItem("station_zhejiang_jin_hua", "金华", BASE + "/publish/radar/zhe-jiang/jin-hua.htm"),
            RadarItem("station_zhejiang_qu_zhou", "衢州", BASE + "/publish/radar/zhe-jiang/qu-zhou.htm"),
            RadarItem("station_zhejiang_tai_zhou", "台州", BASE + "/publish/radar/zhe-jiang/tai-zhou.htm"),
            RadarItem("station_zhejiang_hu_zhou", "湖州", BASE + "/publish/radar/zhe-jiang/hu-zhou.htm"),
            RadarItem("station_zhejiang_longquan", "龙泉", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/zhejiang/longquan/index.html"),
            RadarItem("station_zhejiang_li_shui", "丽水", BASE + "/publish/radar/zhe-jiang/li-shui.htm"),
            RadarItem("station_zhejiang_shaoxing", "绍兴", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/zhejiang/shaoxing/index.html"),
            RadarItem("station_zhejiang_linan", "临安", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/zhejiang/linan/index.html"),
            RadarItem("station_zhejiang_wencheng", "文成", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/zhejiang/wencheng/index.html"),
            RadarItem("station_zhejiang_cheng_si", "嵊泗", BASE + "/publish/radar/zhe-jiang/cheng-si.htm")
        )),
        RadarProvince("anhui", "安徽", listOf(
            RadarItem("station_anhui_he_fei", "合肥", BASE + "/publish/radar/an-hui/he-fei.htm"),
            RadarItem("station_anhui_ma_an_shan", "马鞍山", BASE + "/publish/radar/an-hui/ma-an-shan.htm"),
            RadarItem("station_anhui_fu_yang", "阜阳", BASE + "/publish/radar/an-hui/fu-yang.htm"),
            RadarItem("station_anhui_beng_bu", "蚌埠", BASE + "/publish/radar/an-hui/beng-bu.htm"),
            RadarItem("station_anhui_an_qing", "安庆", BASE + "/publish/radar/an-hui/an-qing.htm"),
            RadarItem("station_anhui_huang_shan", "黄山", BASE + "/publish/radar/an-hui/huang-shan.htm"),
            RadarItem("station_anhui_tong_ling", "铜陵", BASE + "/publish/radar/an-hui/tong-ling.htm"),
            RadarItem("station_anhui_xuancheng", "宣城", BASE + "/publish/radar/an-hui/xuancheng.html")
        )),
        RadarProvince("fujian", "福建", listOf(
            RadarItem("station_fujian_fu_zhou", "福州", BASE + "/publish/radar/fu-jian/fu-zhou.htm"),
            RadarItem("station_fujian_xia_men", "厦门", BASE + "/publish/radar/fu-jian/xia-men.htm"),
            RadarItem("station_fujian_quan_zhou", "泉州", BASE + "/publish/radar/fu-jian/quan-zhou.htm"),
            RadarItem("station_fujian_jian_yang", "建阳", BASE + "/publish/radar/fu-jian/jian-yang.htm"),
            RadarItem("station_fujian_san_ming", "三明", BASE + "/publish/radar/fu-jian/san-ming.htm"),
            RadarItem("station_fujian_long_yan", "龙岩", BASE + "/publish/radar/fu-jian/long-yan.htm"),
            RadarItem("station_fujian_zhang_zhou", "漳州", BASE + "/publish/radar/fu-jian/zhang-zhou.htm"),
            RadarItem("station_fujian_ning_de", "宁德", BASE + "/publish/radar/fu-jian/ning-de.htm")
        )),
        RadarProvince("jiangxi", "江西", listOf(
            RadarItem("station_jiangxi_nan_chang", "南昌", BASE + "/publish/radar/jiang-xi/nan-chang.htm"),
            RadarItem("station_jiangxi_ji_an", "吉安", BASE + "/publish/radar/jiang-xi/ji-an.htm"),
            RadarItem("station_jiangxi_gan_zhou", "赣州", BASE + "/publish/radar/jiang-xi/gan-zhou.htm"),
            RadarItem("station_jiangxi_jiu_jiang", "九江", BASE + "/publish/radar/jiang-xi/jiu-jiang.htm"),
            RadarItem("station_jiangxi_shang_rao", "上饶", BASE + "/publish/radar/jiang-xi/shang-rao.htm"),
            RadarItem("station_jiangxi_yi_chun", "宜春", BASE + "/publish/radar/jiang-xi/yi-chun.htm"),
            RadarItem("station_jiangxi_fu_zhou", "抚州", BASE + "/publish/radar/jiang-xi/fu-zhou.htm"),
            RadarItem("station_jiangxi_jing_de_zhen", "景德镇", BASE + "/publish/radar/jiang-xi/jing-de-zhen.htm")
        )),
        RadarProvince("shandong", "山东", listOf(
            RadarItem("station_shandong_ji_nan", "济南", BASE + "/publish/radar/shan-dong/ji-nan.htm"),
            RadarItem("station_shandong_yan_tai", "烟台", BASE + "/publish/radar/shan-dong/yan-tai.htm"),
            RadarItem("station_shandong_lin_yi", "临沂", BASE + "/publish/radar/shan-dong/lin-yi.htm"),
            RadarItem("station_shandong_bin_zhou", "滨州", BASE + "/publish/radar/shan-dong/bin-zhou.htm"),
            RadarItem("station_shandong_qing_dao", "青岛", BASE + "/publish/radar/shan-dong/qing-dao.htm"),
            RadarItem("station_shandong_tai_shan", "泰山", BASE + "/publish/radar/shan-dong/tai-shan.htm"),
            RadarItem("station_shandong_rong_cheng", "荣成", BASE + "/publish/radar/shan-dong/rong-cheng.htm"),
            RadarItem("station_shandong_wei_fang", "潍坊", BASE + "/publish/radar/shan-dong/wei-fang.htm"),
            RadarItem("station_shandong_liaocheng", "聊城", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/shandong/liaocheng/index.html"),
            RadarItem("station_shandong_ji_ning", "济宁", BASE + "/publish/radar/shan-dong/ji-ning.html")
        )),
        RadarProvince("henan", "河南", listOf(
            RadarItem("station_henan_shang_qiu", "商丘", BASE + "/publish/radar/he-nan/shang-qiu.htm"),
            RadarItem("station_henan_zheng_zhou", "郑州", BASE + "/publish/radar/he-nan/zheng-zhou.htm"),
            RadarItem("station_henan_nan_yang", "南阳", BASE + "/publish/radar/he-nan/nan-yang.htm"),
            RadarItem("station_henan_luo_yang", "洛阳", BASE + "/publish/radar/he-nan/luo-yang.htm"),
            RadarItem("station_henan_pu_yang", "濮阳", BASE + "/publish/radar/he-nan/pu-yang.htm"),
            RadarItem("station_henan_zhu_ma_dian", "驻马店", BASE + "/publish/radar/he-nan/zhu-ma-dian.htm"),
            RadarItem("station_henan_san_men_xia", "三门峡", BASE + "/publish/radar/he-nan/san-men-xia.htm"),
            RadarItem("station_henan_ping_ding_shan", "平顶山", BASE + "/publish/radar/he-nan/ping-ding-shan.htm"),
            RadarItem("station_henan_xinyang", "信阳", BASE + "/publish/radar/henan/xinyang.html")
        )),
        RadarProvince("hubei", "湖北", listOf(
            RadarItem("station_hubei_wu_han", "武汉", BASE + "/publish/radar/hu-bei/wu-han.htm"),
            RadarItem("station_hubei_yi_chang", "宜昌", BASE + "/publish/radar/hu-bei/yi-chang.htm"),
            RadarItem("station_hubei_en_shi", "恩施", BASE + "/publish/radar/hu-bei/en-shi.htm"),
            RadarItem("station_hubei_shi_yan", "十堰", BASE + "/publish/radar/hu-bei/shi-yan.htm"),
            RadarItem("station_hubei_jing_zhou", "荆州", BASE + "/publish/radar/hu-bei/jing-zhou.htm"),
            RadarItem("station_hubei_sui_zhou", "随州", BASE + "/publish/radar/hu-bei/sui-zhou.htm"),
            RadarItem("station_hubei_shen_nong_jia", "神农架", BASE + "/publish/radar/hu-bei/shen-nong-jia.htm"),
            RadarItem("station_hubei_xiang_yang", "襄阳", BASE + "/publish/radar/hu-bei/xiang-yang.htm"),
            RadarItem("station_hubei_ma_cheng", "麻城", BASE + "/publish/radar/hu-bei/ma-cheng.htm"),
            RadarItem("station_hubei_huanggang", "黄冈", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/hubei/huanggang/index.html")
        )),
        RadarProvince("hunan", "湖南", listOf(
            RadarItem("station_hunan_chang_sha", "长沙", BASE + "/publish/radar/hu-nan/chang-sha.htm"),
            RadarItem("station_hunan_chang_de", "常德", BASE + "/publish/radar/hu-nan/chang-de.htm"),
            RadarItem("station_hunan_chen_zhou", "郴州", BASE + "/publish/radar/hu-nan/chen-zhou.htm"),
            RadarItem("station_hunan_yong_zhou", "永州", BASE + "/publish/radar/hu-nan/yong-zhou.htm"),
            RadarItem("station_hunan_yue_yang", "岳阳", BASE + "/publish/radar/hu-nan/yue-yang.htm"),
            RadarItem("station_hunan_shao_yang", "邵阳", BASE + "/publish/radar/hu-nan/shao-yang.htm"),
            RadarItem("station_hunan_huai_hua", "怀化", BASE + "/publish/radar/hu-nan/huai-hua.htm"),
            RadarItem("station_hunan_zhang_jia_jie", "张家界", BASE + "/publish/radar/hu-nan/zhang-jia-jie.htm"),
            RadarItem("station_hunan_xiangtan", "湘潭", BASE + "/publish/radar/hunan/xiangtan.html"),
            RadarItem("station_hunan_hengyang", "衡阳", BASE + "/publish/radar/hunan/hengyang.html"),
            RadarItem("station_hunan_loudi", "娄底", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/hunan/loudi/index.html"),
            RadarItem("station_hunan_zhuzhou", "株洲", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/hunan/zhuzhou/index.html"),
            RadarItem("station_hunan_yiyang", "益阳", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/hunan/yiyang/index.html"),
            RadarItem("station_hunan_xiangxi", "湘西", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/hunan/xiangxi/index.html")
        )),
        RadarProvince("guangdong", "广东", listOf(
            RadarItem("station_guangdong_shangchuandao", "上川岛", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/guangdong/shangchuandao/index.html"),
            RadarItem("station_guangdong_guang_zhou", "广州", BASE + "/publish/radar/guang-dong/guang-zhou.htm"),
            RadarItem("station_guangdong_shao_guan", "韶关", BASE + "/publish/radar/guang-dong/shao-guan.htm"),
            RadarItem("station_guangdong_mei_zhou", "梅州", BASE + "/publish/radar/guang-dong/mei-zhou.htm"),
            RadarItem("station_guangdong_yang_jiang", "阳江", BASE + "/publish/radar/guang-dong/yang-jiang.htm"),
            RadarItem("station_guangdong_shan_tou", "汕头", BASE + "/publish/radar/guang-dong/shan-tou.htm"),
            RadarItem("station_guangdong_shen_zhen", "深圳", BASE + "/publish/radar/guang-dong/shen-zhen.htm"),
            RadarItem("station_guangdong_zhan_jiang", "湛江", BASE + "/publish/radar/guang-dong/zhan-jiang.htm"),
            RadarItem("station_guangdong_he_yuan", "河源", BASE + "/publish/radar/guang-dong/he-yuan.htm"),
            RadarItem("station_guangdong_shan_wei", "汕尾", BASE + "/publish/radar/guang-dong/shan-wei.htm"),
            RadarItem("station_guangdong_zhao_qing", "肇庆", BASE + "/publish/radar/guang-dong/zhao-qing.htm"),
            RadarItem("station_guangdong_lian_zhou", "连州", BASE + "/publish/radar/guang-dong/lian-zhou.htm"),
            RadarItem("station_guangdong_mao_ming", "茂名", BASE + "/publish/radar/guang-dong/mao-ming.htm")
        )),
        RadarProvince("guangxi", "广西", listOf(
            RadarItem("station_guangxi_gui_lin", "桂林", BASE + "/publish/radar/guang-xi/gui-lin.htm"),
            RadarItem("station_guangxi_liu_zhou", "柳州", BASE + "/publish/radar/guang-xi/liu-zhou.htm"),
            RadarItem("station_guangxi_nan_ning", "南宁", BASE + "/publish/radar/guang-xi/nan-ning.htm"),
            RadarItem("station_guangxi_bai_se", "百色", BASE + "/publish/radar/guang-xi/bai-se.htm"),
            RadarItem("station_guangxi_he_chi", "河池", BASE + "/publish/radar/guang-xi/he-chi.htm"),
            RadarItem("station_guangxi_bei_hai", "北海", BASE + "/publish/radar/guang-xi/bei-hai.htm"),
            RadarItem("station_guangxi_wu_zhou", "梧州", BASE + "/publish/radar/guang-xi/wu-zhou.htm"),
            RadarItem("station_guangxi_yu_lin", "玉林", BASE + "/publish/radar/guang-xi/yu-lin.htm"),
            RadarItem("station_guangxi_fang_cheng_gang", "防城港", BASE + "/publish/radar/guang-xi/fang-cheng-gang.htm"),
            RadarItem("station_guangxi_hezhou", "贺州", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/guangxi/hezhou/index.html"),
            RadarItem("station_guangxi_chong_zuo", "崇左", BASE + "/publish/radar/guang-xi/chong-zuo.html")
        )),
        RadarProvince("hainan", "海南", listOf(
            RadarItem("station_hainan_hai_kou", "海口", BASE + "/publish/radar/hai-nan/hai-kou.htm"),
            RadarItem("station_hainan_san_ya", "三亚", BASE + "/publish/radar/hai-nan/san-ya.htm"),
            RadarItem("station_hainan_san_sha", "三沙", BASE + "/publish/radar/hai-nan/san-sha.htm"),
            RadarItem("station_hainan_dongfang", "东方", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/hainan/dongfang/index.html"),
            RadarItem("station_hainan_wanning", "万宁", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/hainan/wanning/index.html")
        )),
        RadarProvince("chongqing", "重庆", listOf(
            RadarItem("station_chongqing_chong_qing", "重庆", BASE + "/publish/radar/chong-qing/chong-qing.htm"),
            RadarItem("station_chongqing_wan_zhou", "万州", BASE + "/publish/radar/chong-qing/wan-zhou.htm"),
            RadarItem("station_chongqing_qian_jiang", "黔江", BASE + "/publish/radar/chong-qing/qian-jiang.htm"),
            RadarItem("station_chongqing_yong_chuan", "永川", BASE + "/publish/radar/chong-qing/yong-chuan.htm"),
            RadarItem("station_chongqing_pei_ling", "涪陵", BASE + "/publish/radar/chong-qing/pei-ling.htm")
        )),
        RadarProvince("sichuan", "四川", listOf(
            RadarItem("station_sichuan_cheng_du", "成都", BASE + "/publish/radar/si-chuan/cheng-du.htm"),
            RadarItem("station_sichuan_hongyuan", "红原", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/sichuan/hongyuan/index.html"),
            RadarItem("station_sichuan_bazhong", "巴中", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/sichuan/bazhong/index.html"),
            RadarItem("station_sichuan_yi_bin", "宜宾", BASE + "/publish/radar/si-chuan/yi-bin.htm"),
            RadarItem("station_sichuan_mian_yang", "绵阳", BASE + "/publish/radar/si-chuan/mian-yang.htm"),
            RadarItem("station_sichuan_nan_chong", "南充", BASE + "/publish/radar/si-chuan/nan-chong.htm"),
            RadarItem("station_sichuan_xi_chang", "西昌", BASE + "/publish/radar/si-chuan/xi-chang.htm"),
            RadarItem("station_sichuan_guang_yuan", "广元", BASE + "/publish/radar/si-chuan/guang-yuan.htm"),
            RadarItem("station_sichuan_da_zhou", "达州", BASE + "/publish/radar/si-chuan/da-zhou.htm"),
            RadarItem("station_sichuan_le_shan", "乐山", BASE + "/publish/radar/si-chuan/le-shan.htm"),
            RadarItem("station_sichuan_kangding", "康定", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/sichuan/kangding/index.html"),
            RadarItem("station_sichuan_ya_an", "雅安", BASE + "/publish/radar/si-chuan/ya-an.htm")
        )),
        RadarProvince("guizhou", "贵州", listOf(
            RadarItem("station_guizhou_gui_yang", "贵阳", BASE + "/publish/radar/gui-zhou/gui-yang.htm"),
            RadarItem("station_guizhou_zun_yi", "遵义", BASE + "/publish/radar/gui-zhou/zun-yi.htm"),
            RadarItem("station_guizhou_tong_ren", "铜仁", BASE + "/publish/radar/gui-zhou/tong-ren.htm"),
            RadarItem("station_guizhou_xing_yi", "兴义", BASE + "/publish/radar/gui-zhou/xing-yi.htm"),
            RadarItem("station_guizhou_bi_jie", "毕节", BASE + "/publish/radar/gui-zhou/bi-jie.htm"),
            RadarItem("station_guizhou_qian_dong_nan", "黔东南", BASE + "/publish/radar/gui-zhou/qian-dong-nan.htm"),
            RadarItem("station_guizhou_dou_yun", "都匀", BASE + "/publish/radar/gui-zhou/dou-yun.htm"),
            RadarItem("station_guizhou_liu_pan_shui", "六盘水", BASE + "/publish/radar/gui-zhou/liu-pan-shui.htm"),
            RadarItem("station_guizhou_rong_jiang", "榕江", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/guizhou/rong-jiang.html"),
            RadarItem("station_guizhou_xi_shui", "习水", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/guizhou/xi-shui.html"),
            RadarItem("station_guizhou_wu_chuan", "务川", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/guizhou/wu-chuan.html")
        )),
        RadarProvince("yunnan", "云南", listOf(
            RadarItem("station_yunnan_qujing", "曲靖", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/yunnan/qujing/index.html"),
            RadarItem("station_yunnan_honghehanizuyizuzizhizhou", "红河哈尼族彝族自治州", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/yunnan/honghehanizuyizuzizhizhou/index.html"),
            RadarItem("station_yunnan_kun_ming", "昆明", BASE + "/publish/radar/yun-nan/kun-ming.htm"),
            RadarItem("station_yunnan_de_hong", "德宏", BASE + "/publish/radar/yun-nan/de-hong.htm"),
            RadarItem("station_yunnan_zhao_tong", "昭通", BASE + "/publish/radar/yun-nan/zhao-tong.htm"),
            RadarItem("station_yunnan_wen_shan", "文山", BASE + "/publish/radar/yun-nan/wen-shan.htm"),
            RadarItem("station_yunnan_si_mao", "思茅", BASE + "/publish/radar/yun-nan/si-mao.htm"),
            RadarItem("station_yunnan_li_jiang", "丽江", BASE + "/publish/radar/yun-nan/li-jiang.htm"),
            RadarItem("station_yunnan_da_li", "大理", BASE + "/publish/radar/yun-nan/da-li.htm"),
            RadarItem("station_yunnan_xishuangbannadaizuzizhizhou", "西双版纳傣族自治州", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/yunnan/xishuangbannadaizuzizhizhou/index.html"),
            RadarItem("station_yunnan_lincang", "临沧", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/yunnan/lincang/index.html"),
            RadarItem("station_yunnan_nujiang", "怒江", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/yunnan/nujiang/index.html")
        )),
        RadarProvince("xizang", "西藏", listOf(
            RadarItem("station_xizang_la_sa", "拉萨", BASE + "/publish/radar/xi-cang/la-sa.htm"),
            RadarItem("station_xizang_lin_zhi", "林芝", BASE + "/publish/radar/xi-cang/lin-zhi.htm"),
            RadarItem("station_xizang_ri_ka_ze", "日喀则", BASE + "/publish/radar/xi-cang/ri-ka-ze.htm"),
            RadarItem("station_xizang_na_qu", "那曲", BASE + "/publish/radar/xi-cang/na-qu.htm")
        )),
        RadarProvince("shaanxi", "陕西", listOf(
            RadarItem("station_shaanxi_xi_an", "西安", BASE + "/publish/radar/shan-xi/xi-an.htm"),
            RadarItem("station_shaanxi_yu_lin", "榆林", BASE + "/publish/radar/shan-xi/yu-lin.htm"),
            RadarItem("station_shaanxi_an_kang", "安康", BASE + "/publish/radar/shan-xi/an-kang.htm"),
            RadarItem("station_shaanxi_yan_an", "延安", BASE + "/publish/radar/shan-xi/yan-an.htm"),
            RadarItem("station_shaanxi_han_zhong", "汉中", BASE + "/publish/radar/shan-xi/han-zhong.htm"),
            RadarItem("station_shaanxi_bao_ji", "宝鸡", BASE + "/publish/radar/shan-xi/bao-ji.htm"),
            RadarItem("station_shaanxi_shang_luo", "商洛", BASE + "/publish/radar/shan-xi/shang-luo.htm"),
            RadarItem("station_shaanxi_weinan", "渭南", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/shanxi/weinan/index.html"),
            RadarItem("station_shaanxi_dingbian", "定边", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/shanxi/dingbian/index.html")
        )),
        RadarProvince("gansu", "甘肃", listOf(
            RadarItem("station_gansu_lan_zhou", "兰州", BASE + "/publish/radar/gan-su/lan-zhou.htm"),
            RadarItem("station_gansu_linxia", "临夏", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/gansu/linxia/index.html"),
            RadarItem("station_gansu_xi_feng", "西峰", BASE + "/publish/radar/gan-su/xi-feng.htm"),
            RadarItem("station_gansu_zhang_ye", "张掖", BASE + "/publish/radar/gan-su/zhang-ye.htm"),
            RadarItem("station_gansu_tian_shui", "天水", BASE + "/publish/radar/gan-su/tian-shui.htm"),
            RadarItem("station_gansu_jia_yu_guan", "嘉峪关", BASE + "/publish/radar/gan-su/jia-yu-guan.htm"),
            RadarItem("station_gansu_gan_nan", "甘南", BASE + "/publish/radar/gan-su/gan-nan.htm"),
            RadarItem("station_gansu_longnan", "陇南", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/gansu/longnan/index.html"),
            RadarItem("station_gansu_dingxi", "定西", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/gansu/dingxi/index.html")
        )),
        RadarProvince("qinghai", "青海", listOf(
            RadarItem("station_qinghai_xi_ning", "西宁", BASE + "/publish/radar/qing-hai/xi-ning.htm"),
            RadarItem("station_qinghai_hai_bei", "海北", BASE + "/publish/radar/qing-hai/hai-bei.htm"),
            RadarItem("station_qinghai_hainan", "海南州", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/qinghai/hainan/index.html"),
            RadarItem("station_qinghai_yushucangzuzizhizhou", "玉树藏族自治州", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/qinghai/yushucangzuzizhizhou/index.html"),
            RadarItem("station_qinghai_huangnancangzuzizhizhou", "黄南藏族自治州", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/qinghai/huangnancangzuzizhizhou/index.html")
        )),
        RadarProvince("ningxia", "宁夏", listOf(
            RadarItem("station_ningxia_yin_chuan", "银川", BASE + "/publish/radar/ning-xia/yin-chuan.htm"),
            RadarItem("station_ningxia_gu_yuan", "固原", BASE + "/publish/radar/ning-xia/gu-yuan.htm"),
            RadarItem("station_ningxia_wu_zhong", "吴忠", BASE + "/publish/radar/ning-xia/wu-zhong.htm")
        )),
        RadarProvince("xinjiang", "新疆", listOf(
            RadarItem("station_xinjiang_tacheng", "塔城", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/xinjiang/tacheng/index.html"),
            RadarItem("station_xinjiang_balikun", "巴里坤", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/xinjiang/balikun/index.html"),
            RadarItem("station_xinjiang_wu_lu_mu_qi", "乌鲁木齐", BASE + "/publish/radar/xin-jiang/wu-lu-mu-qi.htm"),
            RadarItem("station_xinjiang_ke_la_ma_yi", "克拉玛依", BASE + "/publish/radar/xin-jiang/ke-la-ma-yi.htm"),
            RadarItem("station_xinjiang_ku_er_le", "库尔勒", BASE + "/publish/radar/xin-jiang/ku-er-le.htm"),
            RadarItem("station_xinjiang_a_ke_su", "阿克苏", BASE + "/publish/radar/xin-jiang/a-ke-su.htm"),
            RadarItem("station_xinjiang_yi_ning", "伊宁", BASE + "/publish/radar/xin-jiang/yi-ning.htm"),
            RadarItem("station_xinjiang_shi_he_zi", "石河子", BASE + "/publish/radar/xin-jiang/shi-he-zi.htm"),
            RadarItem("station_xinjiang_ka_shen", "喀什", BASE + "/publish/radar/xin-jiang/ka-shen.htm"),
            RadarItem("station_xinjiang_kui_tun", "奎屯", BASE + "/publish/radar/xin-jiang/kui-tun.htm"),
            RadarItem("station_xinjiang_he_tian", "和田", BASE + "/publish/radar/xin-jiang/he-tian.htm"),
            RadarItem("station_xinjiang_wu_jia_qu", "五家渠", BASE + "/publish/radar/xin-jiang/wu-jia-qu.htm"),
            RadarItem("station_xinjiang_tu_mu_shu_ke", "图木舒克", BASE + "/publish/radar/xin-jiang/tu-mu-shu-ke.htm"),
            RadarItem("station_xinjiang_si_ta_er_hai", "塔斯尔海", BASE + "/publish/radar/xin-jiang/si-ta-er-hai.htm"),
            RadarItem("station_xinjiang_hami", "哈密", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/xinjiang/hami/index.html"),
            RadarItem("station_xinjiang_jinghe", "精河", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/xinjiang/jinghe/index.html"),
            RadarItem("station_xinjiang_yizhou", "伊州", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/xinjiang/yizhou/index.html"),
            RadarItem("station_xinjiang_aletai", "阿勒泰", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/xinjiang/aletai/index.html"),
            RadarItem("station_xinjiang_qitai", "奇台", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/xinjiang/qitai/index.html"),
            RadarItem("station_xinjiang_a_la_er", "阿拉尔", BASE + "/publish/radar/xin-jiang/a-la-er.htm"),
            RadarItem("station_xinjiang_xinyuan", "新源", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/xinjiang/xinyuan/index.html"),
            RadarItem("station_xinjiang_hamiyizhouqu", "哈密伊州区", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/xinjiang/hamiyizhouqu/index.html"),
            RadarItem("station_xinjiang_tiemenguan", "铁门关", BASE + "/publish/tianqishikuang/leidatu/danzhanleida/xinjiang/tiemenguan/index.html")
        ))
    )

    private val allStations: List<RadarItem> = provinces.flatMap { it.stations }
    private val aliases = mapOf(
        "station_shenzhen" to "station_guangdong_shen_zhen",
        "station_daxing" to "station_beijing_da_xing",
        "station_nanhui" to "station_shanghai_nan_hui",
        "station_wulumuqi" to "station_xinjiang_wu_lu_mu_qi",
        "station_zhengzhou" to "station_henan_zheng_zhou",
        "station_qingdao" to "station_shandong_qing_dao",
        "station_taiyuan" to "station_shanxi_tai_yuan",
        "station_baicheng" to "station_jilin_bai_cheng",
        "station_xiangyang" to "station_hubei_xiang_yang",
        "station_enshi" to "station_hubei_en_shi",
        "station_ningde" to "station_fujian_ning_de",
        "station_liuzhou" to "station_guangxi_liu_zhou",
        "station_jian" to "station_jiangxi_ji_an",
        "station_bengbu" to "station_anhui_beng_bu"
    )

    fun canonicalKey(key: String): String {
        val migrated = aliases[key] ?: key
        return if (regions.any { it.id == migrated } || allStations.any { it.id == migrated }) migrated else "region_huanan"
    }

    fun item(key: String): RadarItem {
        val canonical = canonicalKey(key)
        return regions.firstOrNull { it.id == canonical }
            ?: allStations.firstOrNull { it.id == canonical }
            ?: regions[5]
    }

    fun url(key: String): String = item(key).url
    fun name(key: String): String = item(key).name
    fun isStation(key: String): Boolean = canonicalKey(key).startsWith("station_")

    fun regionIndex(key: String): Int = regions.indexOfFirst { it.id == canonicalKey(key) }.let { if (it < 0) 5 else it }

    fun provinceIndex(key: String): Int {
        val canonical = canonicalKey(key)
        return provinces.indexOfFirst { province -> province.stations.any { it.id == canonical } }.let { if (it < 0) 0 else it }
    }

    fun stationIndex(provinceIndex: Int, key: String): Int {
        val canonical = canonicalKey(key)
        val stations = provinces.getOrElse(provinceIndex) { provinces[0] }.stations
        return stations.indexOfFirst { it.id == canonical }.let { if (it < 0) 0 else it }
    }
}
