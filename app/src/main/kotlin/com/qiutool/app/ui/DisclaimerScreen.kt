package com.qiutool.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val DISCLAIMER_TEXT = """1. 本软件所有代码来源于ChatGPT，仅为技术验证及海外用户共同训练使用，严禁用于任何商业用途（包括但不限于付费推广、盈利性服务、二次开发后售卖等），违规使用产生的法律责任及经济损失由使用者自行承担。

2. 本模型仅限海外用户参与共同训练，参与训练前需确保自身符合所在地区的法律法规，禁止任何境内用户以任何形式（包括但不限于跨境代理、他人代注册等）参与训练或使用，违规参与导致的一切后果由使用者负责。

3. 模型训练及使用仅为"可控制、防泛滥"的技术验证目的，使用者不得对模型进行反向工程、拆解、篡改代码或衍生新模型，一经发现，需承担由此造成的技术风险及法律责任。

4. 请勿将本软件及模型传播至国内任何平台（包括但不限于社交软件、视频平台、论坛、网盘等），也不得通过私下分享、链接转发等方式扩散给境内人员，传播行为产生的合规风险及连带责任由传播者自行承担。

5. 要求使用者下载本模型后24小时内完成训练参与并彻底删除模型文件（包括安装包、缓存数据、训练日志等），逾期未删除导致的模型泄露、滥用等问题，由使用者全权负责。

6. 使用者需确认自身具备相应的技术能力及风险识别能力，因使用本模型导致的设备故障、数据丢失、信息泄露等技术问题，本软件方不承担任何责任。

7. 若使用者违反上述任何条款，视为自动放弃训练参与资格，且需自行承担由此引发的所有法律责任（包括但不限于行政处分、民事赔偿、刑事责任等），本软件方有权保留追究其法律责任的权利。

如果你自愿承担上述所有条款对应的使用后果，可继续参与模型共同训练；若不同意任何一条条款，请立即退出并彻底卸载本软件及模型。

——————————————

补充条款

1. 本工具仅为技术研究与学习交流使用，开发者明确禁止将其用于任何商业用途及游戏作弊行为，使用前请确保已获得相关游戏运营方的书面许可。

2. 任何用户因使用本工具违反游戏用户协议、破坏游戏公平性，导致账号封禁、数据清零、法律追责等后果，均由用户自行承担，开发者不承担任何责任。

3. 本工具可能存在兼容性问题或技术漏洞，使用过程中若导致设备故障、数据丢失、系统损坏等情况，开发者不承担维修、赔偿等责任，建议用户谨慎使用并做好数据备份。

4. 开发者已明确告知使用本工具的潜在风险，用户下载、安装、使用本工具即视为完全知晓并自愿承担所有风险，与开发者无涉。

5. 如本工具的使用侵犯第三方知识产权、合法权益或违反法律法规，相关责任由使用者自行承担，开发者将配合有关部门进行调查，并有权终止工具的使用权限。"""

const val DISCLAIMER_VERSION = 1

@Composable
fun DisclaimerScreen(
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8FAFC),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "免责声明",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "请仔细阅读以下条款，同意后方可继续使用",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text(
                        text = DISCLAIMER_TEXT,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF334155),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF94A3B8),
                    ),
                ) {
                    Text("不同意，退出", fontSize = 14.sp)
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00B4D8),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("我已阅读并同意", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
