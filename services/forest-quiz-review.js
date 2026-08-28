// Public review content used while an older backend is still being deployed.
// Answers and explanations are intentionally omitted.
module.exports = {
  morphology: {
    title: '林蛙长什么样？', topic: '形态特征',
    knowledgePoints: ['东北林蛙属于无尾目蛙科林蛙属两栖动物，又称林蛙、蛤士蟆、雪蛤。', '体长约71—90毫米，头扁平，吻端钝圆。', '体色多变，背面皮肤较光滑，鼓膜部位有三角形黑斑。'],
    questions: [
      { id: 'm1', prompt: '东北林蛙属于什么科？', options: [{ id: 'a', text: '蛙科' }, { id: 'b', text: '蟾蜍科' }, { id: 'c', text: '树蛙科' }] },
      { id: 'm2', prompt: '以下哪个是林蛙的别称？', options: [{ id: 'a', text: '癞蛤蟆' }, { id: 'b', text: '雪蛤' }, { id: 'c', text: '树蛙' }] },
      { id: 'm3', prompt: '林蛙的体色是单一的吗？', options: [{ id: 'a', text: '是，只有灰色' }, { id: 'b', text: '不是，体色多变' }] },
    ],
  },
  distribution: {
    title: '林蛙住在哪？', topic: '分布与栖息地',
    knowledgePoints: ['国内主要分布于黑龙江、吉林、辽宁和内蒙古东北部。', '国外分布于俄罗斯远东、蒙古东部、朝鲜、日本对马岛。', '喜欢阔叶林、针叶林和混交林中郁闭度大、落叶多、空气湿润的环境。'],
    questions: [
      { id: 'd1', prompt: '吉林省内以下哪个地方有林蛙分布？', options: [{ id: 'a', text: '长春' }, { id: 'b', text: '吉林市' }, { id: 'c', text: '四平' }] },
      { id: 'd2', prompt: '林蛙喜欢什么样的森林环境？', options: [{ id: 'a', text: '郁闭度大、枯枝落叶多、空气湿润' }, { id: 'b', text: '开阔干燥、几乎没有落叶' }, { id: 'c', text: '只有城市水泥地' }] },
      { id: 'd3', prompt: '以下哪个国家没有林蛙分布？', options: [{ id: 'a', text: '俄罗斯' }, { id: 'b', text: '日本' }, { id: 'c', text: '印度' }] },
    ],
  },
  diet: {
    title: '林蛙吃什么？', topic: '食性与生态价值',
    knowledgePoints: ['林蛙是肉食性动物，主要捕食昆虫及其他小动物。', '每只野生林蛙每年可捕食昆虫3万只以上。', '林蛙被称为森林卫士，对防治病虫害、维持生态平衡很重要。'],
    questions: [
      { id: 'e1', prompt: '林蛙的食性是什么？', options: [{ id: 'a', text: '草食性' }, { id: 'b', text: '肉食性' }, { id: 'c', text: '杂食性' }] },
      { id: 'e2', prompt: '一只林蛙每年能捕食多少害虫？', options: [{ id: 'a', text: '300只' }, { id: 'b', text: '3000只' }, { id: 'c', text: '3万只以上' }] },
      { id: 'e3', prompt: '林蛙被称为什么？', options: [{ id: 'a', text: '森林卫士' }, { id: 'b', text: '田间歌唱家' }, { id: 'c', text: '水中猎手' }] },
    ],
  },
  hibernation: {
    title: '林蛙怎么过冬？', topic: '冬眠习性',
    knowledgePoints: ['冬眠期大致从11月末至翌年3月中下旬，林蛙会在水底冬眠。', '它们成群聚集在河水深处的大石块下，长白山林蛙可在雪地下冬眠100多天。', '林蛙可耐受—20℃，气温回升至10℃—15℃时解除冬眠。'],
    questions: [
      { id: 'h1', prompt: '林蛙冬眠时在哪里？', options: [{ id: 'a', text: '水底大石块下' }, { id: 'b', text: '树梢上' }, { id: 'c', text: '沙漠地表' }] },
      { id: 'h2', prompt: '林蛙为什么叫“雪蛤”？', options: [{ id: 'a', text: '皮肤像雪一样白' }, { id: 'b', text: '冬天在雪地下冬眠100多天' }, { id: 'c', text: '只在下雪天捕食' }] },
      { id: 'h3', prompt: '林蛙耐寒能力有多强？', options: [{ id: 'a', text: '—5℃' }, { id: 'b', text: '—20℃' }, { id: 'c', text: '—40℃' }] },
    ],
  },
  reproduction: {
    title: '林蛙如何繁衍？', topic: '繁殖与生命周期',
    knowledgePoints: ['繁殖期为每年4月初至5月初，清明前后解冻出蛰。', '产卵多在静水塘或流溪水凼内，每个卵团含卵500—2300粒。', '从产卵到变态成为幼蛙约需60—70天。'],
    questions: [
      { id: 'r1', prompt: '林蛙的繁殖期在什么时候？', options: [{ id: 'a', text: '1月初至2月初' }, { id: 'b', text: '4月初至5月初' }, { id: 'c', text: '9月初至10月初' }] },
      { id: 'r2', prompt: '每个卵团含多少粒卵？', options: [{ id: 'a', text: '50—230粒' }, { id: 'b', text: '500—2300粒' }, { id: 'c', text: '5000—23000粒' }] },
      { id: 'r3', prompt: '从卵到幼蛙需要多少天？', options: [{ id: 'a', text: '6—7天' }, { id: 'b', text: '30—40天' }, { id: 'c', text: '60—70天' }] },
    ],
  },
  protection: {
    title: '林蛙为什么重要？', topic: '保护现状与产业价值',
    knowledgePoints: ['林蛙在IUCN红色名录中被评为近危（NT），也被列入中国濒危动物红皮书。', '林蛙是国家“三有”保护动物，具有重要生态、科学和社会价值。', '主要威胁包括滥捕乱捞、森林过度砍伐、工业废水以及农药化肥。'],
    questions: [
      { id: 'p1', prompt: 'IUCN红色名录中林蛙被评为哪个级别？', options: [{ id: 'a', text: '无危（LC）' }, { id: 'b', text: '近危（NT）' }, { id: 'c', text: '灭绝（EX）' }] },
      { id: 'p2', prompt: '以下哪个不是林蛙面临的主要威胁？', options: [{ id: 'a', text: '森林过度砍伐' }, { id: 'b', text: '工业废水排放' }, { id: 'c', text: '全球变暖' }] },
      { id: 'p3', prompt: '林蛙是国家几级保护动物？', options: [{ id: 'a', text: '国家一级' }, { id: 'b', text: '国家二级' }, { id: 'c', text: '三有保护动物' }] },
    ],
  },
}
