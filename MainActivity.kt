package ir.bimeh.installments

import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

private data class Customer(val id: Long, val name: String, val phone: String, val nationalId: String, val note: String)
private data class Policy(val id: Long, val customerId: Long, val number: String, val type: String, val premium: Long)
private data class Installment(val id: Long, val customerId: Long, val policyId: Long, val number: Int, val amount: Long, val due: String, val paid: Boolean, val paidDate: String, val receipt: String)

private class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("bimeh_pro", Context.MODE_PRIVATE)
    var customers by mutableStateOf(loadCustomers()); private set
    var policies by mutableStateOf(loadPolicies()); private set
    var installments by mutableStateOf(loadInstallments()); private set
    var pin by mutableStateOf(p.getString("pin", null)); private set

    private fun loadCustomers(): List<Customer> = runCatching { val a=JSONArray(p.getString("customers","[]")); List(a.length()){val o=a.getJSONObject(it); Customer(o.getLong("id"),o.getString("name"),o.optString("phone"),o.optString("nid"),o.optString("note"))} }.getOrDefault(emptyList())
    private fun loadPolicies(): List<Policy> = runCatching { val a=JSONArray(p.getString("policies","[]")); List(a.length()){val o=a.getJSONObject(it); Policy(o.getLong("id"),o.getLong("customer"),o.getString("number"),o.getString("type"),o.getLong("premium"))} }.getOrDefault(emptyList())
    private fun loadInstallments(): List<Installment> = runCatching { val a=JSONArray(p.getString("installments","[]")); List(a.length()){val o=a.getJSONObject(it); Installment(o.getLong("id"),o.getLong("customer"),o.getLong("policy"),o.getInt("number"),o.getLong("amount"),o.getString("due"),o.getBoolean("paid"),o.optString("paidDate"),o.optString("receipt"))} }.getOrDefault(emptyList())
    private fun save() { p.edit().putString("customers", JSONArray().apply{customers.forEach{put(JSONObject().apply{put("id",it.id);put("name",it.name);put("phone",it.phone);put("nid",it.nationalId);put("note",it.note)})}}.toString()).putString("policies", JSONArray().apply{policies.forEach{put(JSONObject().apply{put("id",it.id);put("customer",it.customerId);put("number",it.number);put("type",it.type);put("premium",it.premium)})}}.toString()).putString("installments", JSONArray().apply{installments.forEach{put(JSONObject().apply{put("id",it.id);put("customer",it.customerId);put("policy",it.policyId);put("number",it.number);put("amount",it.amount);put("due",it.due);put("paid",it.paid);put("paidDate",it.paidDate);put("receipt",it.receipt)})}}.toString()).apply() }
    fun setPin(v:String){pin=v;p.edit().putString("pin",v).apply()}
    fun addCustomer(c:Customer){customers=customers+c;save()}; fun deleteCustomer(id:Long){customers=customers.filterNot{it.id==id};policies=policies.filterNot{it.customerId==id};installments=installments.filterNot{it.customerId==id};save()}
    fun addPolicy(x:Policy){policies=policies+x;save()}; fun deletePolicy(id:Long){policies=policies.filterNot{it.id==id};installments=installments.filterNot{it.policyId==id};save()}
    fun addInstallment(x:Installment){installments=installments+x;save()}; fun togglePaid(id:Long){installments=installments.map{if(it.id==id)it.copy(paid=!it.paid,paidDate=if(!it.paid)today() else "")else it};save()}; fun deleteInstallment(id:Long){installments=installments.filterNot{it.id==id};save()}
    fun exportJson():String=JSONObject().apply{put("customers",JSONArray().apply{customers.forEach{put(JSONObject().apply{put("id",it.id);put("name",it.name);put("phone",it.phone);put("nid",it.nationalId);put("note",it.note)})}});put("policies",JSONArray().apply{policies.forEach{put(JSONObject().apply{put("id",it.id);put("customer",it.customerId);put("number",it.number);put("type",it.type);put("premium",it.premium)})}});put("installments",JSONArray().apply{installments.forEach{put(JSONObject().apply{put("id",it.id);put("customer",it.customerId);put("policy",it.policyId);put("number",it.number);put("amount",it.amount);put("due",it.due);put("paid",it.paid);put("paidDate",it.paidDate);put("receipt",it.receipt)})}})}.toString(2)
    fun importJson(s:String){val o=JSONObject(s); val ca=o.getJSONArray("customers"); customers=List(ca.length()){val x=ca.getJSONObject(it);Customer(x.getLong("id"),x.getString("name"),x.optString("phone"),x.optString("nid"),x.optString("note"))}; val pa=o.getJSONArray("policies");policies=List(pa.length()){val x=pa.getJSONObject(it);Policy(x.getLong("id"),x.getLong("customer"),x.getString("number"),x.getString("type"),x.getLong("premium"))}; val ia=o.getJSONArray("installments");installments=List(ia.length()){val x=ia.getJSONObject(it);Installment(x.getLong("id"),x.getLong("customer"),x.getLong("policy"),x.getInt("number"),x.getLong("amount"),x.getString("due"),x.getBoolean("paid"),x.optString("paidDate"),x.optString("receipt"))};save()}
    private fun today()="امروز"
}


private class CloudSync {
    private val url = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_KEY
    private fun request(method:String, body:String?=null):String {
        val c=(URL("$url/rest/v1/cloud_snapshots?id=eq.1").openConnection() as HttpURLConnection)
        c.requestMethod=method; c.setRequestProperty("apikey",key); c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("Prefer","return=representation"); c.connectTimeout=15000; c.readTimeout=15000
        if(body!=null){c.doOutput=true; OutputStreamWriter(c.outputStream).use{it.write(body)}}
        val stream=if(c.responseCode in 200..299)c.inputStream else c.errorStream
        return stream?.bufferedReader()?.use{it.readText()} ?: ""
    }
    fun push(payload:String):Boolean=runCatching{ request("PATCH", JSONObject().put("payload",JSONObject(payload)).put("updated_at", "now()").toString()); true }.getOrDefault(false)
    fun pull():String?=runCatching{ val r=request("GET"); val a=JSONArray(r); if(a.length()==0)null else a.getJSONObject(0).optJSONObject("payload")?.toString() }.getOrNull()
}

class MainActivity: ComponentActivity(){ override fun onCreate(b:Bundle?){super.onCreate(b);setContent{BimehApp()}} }

@Composable private fun BimehApp(){
    val ctx=LocalContext.current; val store=remember{Store(ctx)}; var unlocked by remember{mutableStateOf(store.pin==null)}
    MaterialTheme { CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl){ if(!unlocked) Login(store){unlocked=true} else Home(store) } }
}

@Composable private fun Login(store:Store,onOk:()->Unit){var pin by remember{mutableStateOf("")};var first by remember{mutableStateOf(true)};var err by remember{mutableStateOf("")}; LaunchedEffect(store.pin){first=store.pin==null}
    Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text("مدیریت اقساط بیمه",style=MaterialTheme.typography.headlineMedium);Text(if(first)"برای شروع رمز مدیر را تعیین کنید" else "ورود به برنامه",Modifier.padding(top=8.dp));Spacer(Modifier.height(24.dp));OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(8)},label={Text("رمز")},visualTransformation=PasswordVisualTransformation(),singleLine=true);if(err.isNotEmpty())Text(err,color=MaterialTheme.colorScheme.error);Spacer(Modifier.height(16.dp));Button(onClick={if(pin.length<4)err="رمز حداقل ۴ رقم باشد" else if(first){store.setPin(pin);onOk()} else if(pin==store.pin)onOk() else err="رمز اشتباه است"},modifier=Modifier.fillMaxWidth()){Text(if(first)"شروع امن" else "ورود")}}}
}

private enum class Tab(val title:String,val icon:@Composable ()->Unit){D("داشبورد",{Icon(Icons.Default.Home,null)}),C("بیمه‌گذاران",{Icon(Icons.Default.People,null)}),P("بیمه‌نامه‌ها",{Icon(Icons.Default.Description,null)}),I("اقساط",{Icon(Icons.Default.Payments,null)}),R("گزارش",{Icon(Icons.Default.BarChart,null)}),S("تنظیمات",{Icon(Icons.Default.Settings,null)})}

@Composable private fun Home(store:Store){var tab by remember{mutableStateOf(Tab.D)};Scaffold(bottomBar={NavigationBar{Tab.values().forEach{NavigationBarItem(selected=tab==it,onClick={tab=it},icon=it.icon,label={Text(it.title)})}}}){pad->Box(Modifier.padding(pad)){when(tab){Tab.D->Dashboard(store);Tab.C->Customers(store);Tab.P->Policies(store);Tab.I->Installments(store);Tab.R->Reports(store);Tab.S->Settings(store)}}}}

@Composable private fun Dashboard(s:Store){val paid=s.installments.filter{it.paid}.sumOf{it.amount};val debt=s.installments.filterNot{it.paid}.sumOf{it.amount};LazyColumn(Modifier.fillMaxSize().padding(16.dp)){item{Text("داشبورد مالی",style=MaterialTheme.typography.headlineSmall);Text("نمای کلی وضعیت پرونده‌ها",color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(16.dp))};item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("بیمه‌گذاران",s.customers.size.toString());Stat("بیمه‌نامه",s.policies.size.toString())}};item{Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("دریافتی",money(paid));Stat("بدهی",money(debt))}};item{Spacer(Modifier.height(18.dp));Text("اقساط نیازمند پیگیری",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(8.dp))};items(s.installments.filterNot{it.paid}.take(8)){x->ListCard("قسط ${x.number} • ${x.due}",money(x.amount),"پرداخت نشده")}}}
@Composable private fun Stat(a:String,b:String){Card(Modifier.weight(1f)){Column(Modifier.padding(16.dp)){Text(a,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(b,style=MaterialTheme.typography.titleLarge)}}}

@Composable private fun Customers(s:Store){var add by remember{mutableStateOf(false)};var q by remember{mutableStateOf("")};Column(Modifier.fillMaxSize().padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("بیمه‌گذاران",style=MaterialTheme.typography.headlineSmall,Modifier.weight(1f));FloatingActionButton(onClick={add=true}){Icon(Icons.Default.Add,null)}};OutlinedTextField(q,{q=it},label={Text("جستجو")},modifier=Modifier.fillMaxWidth().padding(vertical=10.dp));LazyColumn{items(s.customers.filter{it.name.contains(q,true)||it.phone.contains(q)||it.nationalId.contains(q)}){c->ListCard(c.name,"${c.phone}  •  ${c.nationalId}","بدهی: ${money(s.installments.filter{it.customerId==c.id&&!it.paid}.sumOf{it.amount})}",onDelete={s.deleteCustomer(c.id)})}}};if(add)CustomerDialog({add=false}){s.addCustomer(it);add=false}}

@Composable private fun Policies(s:Store){var add by remember{mutableStateOf(false)};Column(Modifier.fillMaxSize().padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("بیمه‌نامه‌ها",style=MaterialTheme.typography.headlineSmall,Modifier.weight(1f));Button({add=true}){Icon(Icons.Default.Add,null);Text(" جدید")}};LazyColumn{items(s.policies){p->val c=s.customers.find{it.id==p.customerId};ListCard("بیمه‌نامه ${p.number}","${c?.name?:("شناسه "+p.customerId)} • ${p.type}","حق‌بیمه: ${money(p.premium)}",onDelete={s.deletePolicy(p.id)})}}};if(add)PolicyDialog(s.customers,{add=false}){s.addPolicy(it);add=false}}

@Composable private fun Installments(s:Store){var add by remember{mutableStateOf(false)};var onlyUnpaid by remember{mutableStateOf(false)};Column(Modifier.fillMaxSize().padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("اقساط",style=MaterialTheme.typography.headlineSmall,Modifier.weight(1f));Button({add=true}){Icon(Icons.Default.Add,null);Text(" قسط")}};Row(verticalAlignment=Alignment.CenterVertically){Checkbox(onlyUnpaid,{onlyUnpaid=it});Text("فقط پرداخت‌نشده")};LazyColumn{items(s.installments.filter{!onlyUnpaid||!it.paid}.sortedBy{it.due}){x->val c=s.customers.find{it.id==x.customerId};ListCard("${c?.name?:("مشتری "+x.customerId)} • قسط ${x.number}","سررسید: ${x.due} • ${money(x.amount)}",if(x.paid)"پرداخت شده" else "پرداخت نشده",onClick={s.togglePaid(x.id)},onDelete={s.deleteInstallment(x.id)})}}};if(add)InstallmentDialog(s.customers,s.policies,{add=false}){s.addInstallment(it);add=false}}

@Composable private fun Reports(s:Store){val total=s.installments.sumOf{it.amount};val paid=s.installments.filter{it.paid}.sumOf{it.amount};val debt=total-paid;LazyColumn(Modifier.fillMaxSize().padding(16.dp)){item{Text("گزارش مالی",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(16.dp))};item{ReportRow("کل اقساط",money(total));ReportRow("دریافتی",money(paid));ReportRow("مانده بدهی",money(debt));ReportRow("پرداخت شده",s.installments.count{it.paid}.toString());ReportRow("در انتظار پرداخت",s.installments.count{!it.paid}.toString())};item{Spacer(Modifier.height(16.dp));Text("بدهی بیمه‌گذاران",style=MaterialTheme.typography.titleLarge)};items(s.customers.sortedByDescending{c->s.installments.filter{it.customerId==c.id&&!it.paid}.sumOf{it.amount}}){c->val d=s.installments.filter{it.customerId==c.id&&!it.paid}.sumOf{it.amount};if(d>0)ListCard(c.name,money(d),"مانده بدهی")}}}
@Composable private fun ReportRow(a:String,b:String){Row(Modifier.fillMaxWidth().padding(vertical=8.dp)){Text(a,Modifier.weight(1f));Text(b,style=MaterialTheme.typography.titleMedium)}}

@Composable private fun Settings(s:Store){
    val ctx=LocalContext.current; var change by remember{mutableStateOf(false)}; var export by remember{mutableStateOf(false)}; var syncMsg by remember{mutableStateOf("")}; var syncing by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().padding(16.dp)){Text("تنظیمات",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(12.dp));
        Button({change=true},Modifier.fillMaxWidth()){Text("تغییر رمز ورود")};Spacer(Modifier.height(8.dp));
        Button({export=true},Modifier.fillMaxWidth()){Text("پشتیبان‌گیری JSON")};Spacer(Modifier.height(8.dp));
        Button(enabled=!syncing,onClick={syncing=true;syncMsg="در حال همگام‌سازی...";val json=s.exportJson();Thread{val ok=CloudSync().push(json);Handler(Looper.getMainLooper()).post{syncing=false;syncMsg=if(ok)"همگام‌سازی ابری با موفقیت انجام شد" else "اتصال ابری ناموفق بود"}}.start()},Modifier.fillMaxWidth()){Text(if(syncing)"در حال اتصال..." else "همگام‌سازی ابری")}
        if(syncMsg.isNotEmpty())Text(syncMsg,Modifier.padding(top=10.dp),color=MaterialTheme.colorScheme.primary)
        Text("نسخه 3.0.0 • آفلاین + پشتیبان ابری Supabase",Modifier.padding(top=20.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
    };if(change)PinDialog(s,{change=false});if(export){ExportDialog(s.exportJson(),ctx){export=false}}
}

@Composable private fun ListCard(title:String,sub:String,status:String,onClick:(()->Unit)?=null,onDelete:(()->Unit)?=null){Card(Modifier.fillMaxWidth().padding(vertical=5.dp).clickable(enabled=onClick!=null){onClick?.invoke()}){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium);Text(sub,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(status,color=if(status.contains("پرداخت شده"))MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)}};if(onDelete!=null)IconButton(onClick=onDelete){Icon(Icons.Default.Delete,null)}}}

@Composable private fun CustomerDialog(close:()->Unit,save:(Customer)->Unit){var n by remember{mutableStateOf("")};var ph by remember{mutableStateOf("")};var nid by remember{mutableStateOf("")};AlertDialog(onDismissRequest=close,title={Text("بیمه‌گذار جدید")},text={Column{Field("نام و نام خانوادگی",n,{n=it});Field("موبایل",ph,{ph=it});Field("کد ملی",nid,{nid=it})}},confirmButton={Button({if(n.isNotBlank())save(Customer(System.currentTimeMillis(),n,ph,nid,""))}){Text("ثبت")}},dismissButton={TextButton(close){Text("انصراف")}})}
@Composable private fun PolicyDialog(cs:List<Customer>,close:()->Unit,save:(Policy)->Unit){var num by remember{mutableStateOf("")};var type by remember{mutableStateOf("ثالث")};var prem by remember{mutableStateOf("")};var cid by remember{mutableStateOf(cs.firstOrNull()?.id?:0)};AlertDialog(onDismissRequest=close,title={Text("بیمه‌نامه جدید")},text={Column{Field("شماره بیمه‌نامه",num,{num=it});Field("نوع بیمه",type,{type=it});Field("حق‌بیمه",prem,{prem=it})}},confirmButton={Button({if(num.isNotBlank())save(Policy(System.currentTimeMillis(),cid,num,type,prem.filter(Char::isDigit).toLongOrNull()?:0))}){Text("ثبت")}},dismissButton={TextButton(close){Text("انصراف")}})}
@Composable private fun InstallmentDialog(cs:List<Customer>,ps:List<Policy>,close:()->Unit,save:(Installment)->Unit){var amt by remember{mutableStateOf("")};var due by remember{mutableStateOf("")};var num by remember{mutableStateOf("1")};var cid by remember{mutableStateOf(cs.firstOrNull()?.id?:0)};var pid by remember{mutableStateOf(ps.firstOrNull()?.id?:0)};AlertDialog(onDismissRequest=close,title={Text("قسط جدید")},text={Column{Field("شماره قسط",num,{num=it});Field("مبلغ",amt,{amt=it});Field("تاریخ سررسید (شمسی)",due,{due=it})}},confirmButton={Button({save(Installment(System.currentTimeMillis(),cid,pid,num.toIntOrNull()?:1,amt.filter(Char::isDigit).toLongOrNull()?:0,due,false,"",""))}){Text("ثبت")}},dismissButton={TextButton(close){Text("انصراف")}})}
@Composable private fun PinDialog(s:Store,close:()->Unit){var p by remember{mutableStateOf("")};AlertDialog(onDismissRequest=close,title={Text("تغییر رمز")},text={OutlinedTextField(p,{p=it.filter(Char::isDigit).take(8)},label={Text("رمز جدید")},visualTransformation=PasswordVisualTransformation())},confirmButton={Button({if(p.length>=4){s.setPin(p);close()}}){Text("ذخیره")}},dismissButton={TextButton(close){Text("انصراف")}})}
@Composable private fun ExportDialog(data:String,ctx:Context,close:()->Unit){AlertDialog(onDismissRequest=close,title={Text("پشتیبان آماده است")},text={Text("اطلاعات در قالب JSON آماده خروجی است. برای نسخه نهایی، انتخاب محل ذخیره فایل را به این پروژه اضافه می‌کنیم.")},confirmButton={Button({val i=android.content.Intent(android.content.Intent.ACTION_SEND).apply{type="application/json";putExtra(android.content.Intent.EXTRA_TEXT,data)};ctx.startActivity(android.content.Intent.createChooser(i,"ارسال پشتیبان"));close()}){Text("اشتراک‌گذاری")}},dismissButton={TextButton(close){Text("بستن")}})}
@Composable private fun Field(label:String,v:String,on:(String)->Unit){OutlinedTextField(v,on,label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth().padding(vertical=3.dp))}
private fun money(v:Long)=NumberFormat.getNumberInstance(Locale("fa","IR")).format(v)+" تومان"
