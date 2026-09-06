package ir.bimeh.installments

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

private const val DEFAULT_PIN = "1234"

data class Installment(
    val id: Long,
    val customer: String,
    val phone: String,
    val policy: String,
    val amount: Long,
    val due: String,
    val paid: Boolean
)

class Store(context: Context) {
    private val p = context.getSharedPreferences("bimeh_store", Context.MODE_PRIVATE)
    fun pin() = p.getString("pin", DEFAULT_PIN) ?: DEFAULT_PIN
    fun setPin(v: String) { p.edit().putString("pin", v).apply() }
    fun load(): List<Installment> = runCatching {
        val a = JSONArray(p.getString("items", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Installment(o.getLong("id"), o.getString("customer"), o.optString("phone"), o.getString("policy"), o.getLong("amount"), o.getString("due"), o.getBoolean("paid"))
        }
    }.getOrDefault(emptyList())
    fun save(items: List<Installment>) {
        val a = JSONArray()
        items.forEach { x -> a.put(JSONObject().apply { put("id",x.id);put("customer",x.customer);put("phone",x.phone);put("policy",x.policy);put("amount",x.amount);put("due",x.due);put("paid",x.paid) }) }
        p.edit().putString("items", a.toString()).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App(Store(this)) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App(store: Store) {
    var logged by remember { mutableStateOf(false) }
    if (!logged) { LoginScreen(store) { logged = true }; return }
    var items by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Installment?>(null) }
    var settings by remember { mutableStateOf(false) }
    val titles = listOf("داشبورد","اقساط","بیمه‌گذاران","گزارش")
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        Scaffold(
            topBar = { TopAppBar(title={ Text(titles[tab]) }, actions={
                IconButton(onClick={settings=true}) { Icon(Icons.Default.Settings, null) }
                IconButton(onClick={ { edit=null; dialog=true } }) { Icon(Icons.Default.Add, "افزودن") }
            })},
            bottomBar={ NavigationBar { val icons=listOf(Icons.Default.Home,Icons.Default.List,Icons.Default.People,Icons.Default.Assessment); icons.forEachIndexed{ i,ic -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(ic,null)},label={Text(titles[i])}) } } }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when(tab) {
                    0 -> Dashboard(items)
                    1 -> Installments(items, { x -> items=x; store.save(x) }, { x -> edit=x; dialog=true })
                    2 -> Customers(items)
                    3 -> Report(items)
                }
            }
        }
        if (dialog) EntryDialog(edit, { dialog=false }) { x ->
            items = if (edit == null) items + x else items.map { if (it.id == x.id) x else it }
            store.save(items); dialog=false
        }
        if (settings) SettingsDialog(store, {settings=false})
    }
}

@Composable fun LoginScreen(store: Store, onSuccess:()->Unit) {
    var pin by remember { mutableStateOf("") }; var error by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment=Alignment.Center) {
        Card(shape=RoundedCornerShape(28.dp), modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Default.Security, null, modifier=Modifier.size(58.dp)); Text("مدیریت اقساط بیمه", style=MaterialTheme.typography.headlineSmall); Text("ورود به سامانه", fontSize=15.sp)
            OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(8)},label={Text("رمز ورود")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            if(error) Text("رمز ورود اشتباه است", color=MaterialTheme.colorScheme.error)
            Button(onClick={ if(pin==store.pin()) onSuccess() else error=true }, modifier=Modifier.fillMaxWidth()) { Text("ورود") }
            Text("رمز اولیه: 1234", fontSize=12.sp)
        } }
    }
}

@Composable fun Dashboard(items: List<Installment>) {
    val total=items.sumOf{it.amount}; val paid=items.filter{it.paid}.sumOf{it.amount}; val debt=total-paid
    LazyColumn(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("مدیریت اقساط بیمه",style=MaterialTheme.typography.headlineSmall); Text("کنترل سریع وضعیت پرونده‌ها و دریافتی‌ها") }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){ Stat("کل اقساط",items.size.toString()); Stat("دریافتی",money(paid)); Stat("بدهی",money(debt)) } }
        item { Text("آخرین اقساط",style=MaterialTheme.typography.titleLarge) }
        items(items.sortedByDescending{it.id}.take(8)) { x -> Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(x.customer,style=MaterialTheme.typography.titleMedium);Text("بیمه‌نامه ${x.policy} • سررسید ${x.due}");Text(money(x.amount));Text(if(x.paid)"✓ پرداخت شده" else "● پرداخت نشده")}} }
    }
}

@Composable fun Stat(label:String,value:String){Card(Modifier.weight(1f)){Column(Modifier.padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(label,fontSize=12.sp);Text(value,style=MaterialTheme.typography.titleMedium)}}}

@Composable fun Installments(items:List<Installment>, onChange:(List<Installment>)->Unit, onEdit:(Installment)->Unit){
    var q by remember{mutableStateOf("")}; var delete by remember{mutableStateOf<Installment?>(null)}
    val filtered=items.filter{it.customer.contains(q,true)||it.policy.contains(q,true)||it.phone.contains(q,true)}
    Column(Modifier.padding(12.dp)){ OutlinedTextField(q,{q=it},Modifier.fillMaxWidth(),label={Text("جستجوی نام، تلفن یا بیمه‌نامه")},singleLine=true); Spacer(Modifier.height(8.dp));
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){ items(filtered){x-> Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(x.customer,style=MaterialTheme.typography.titleMedium);Text("${x.policy} • ${x.due}");Text(money(x.amount));if(x.phone.isNotBlank())Text(x.phone,fontSize=12.sp)} IconButton(onClick={onEdit(x)}){Icon(Icons.Default.Edit,null)} IconButton(onClick={delete=x}){Icon(Icons.Default.Delete,null)} }
            if(x.paid) Text("✓ پرداخت شده") else Button(onClick={onChange(items.map{if(it.id==x.id)it.copy(paid=true)else it})},modifier=Modifier.fillMaxWidth()){Text("ثبت پرداخت")}
        } } } }
    }
    delete?.let { x -> AlertDialog(onDismissRequest={delete=null},title={Text("حذف قسط")},text={Text("قسط ${x.customer} حذف شود؟")},confirmButton={Button(onClick={onChange(items.filter{it.id!=x.id});delete=null}){Text("حذف")}},dismissButton={TextButton(onClick={delete=null}){Text("انصراف")}}) }
}

@Composable fun Customers(items:List<Installment>){ val groups=items.groupBy{it.customer}; LazyColumn(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){items(groups.entries.toList()){(name,list)->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(name,style=MaterialTheme.typography.titleLarge);Text("${list.size} قسط • بدهی ${money(list.filter{!it.paid}.sumOf{it.amount})}"); list.firstOrNull{it.phone.isNotBlank()}?.let{Text(it.phone)}}}}} }

@Composable fun Report(items:List<Installment>){ val total=items.sumOf{it.amount};val paid=items.filter{it.paid}.sumOf{it.amount}; LazyColumn(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("گزارش مالی",style=MaterialTheme.typography.headlineSmall)};item{ReportRow("تعداد کل اقساط",items.size.toString());ReportRow("پرداخت شده",items.count{it.paid}.toString());ReportRow("باقی مانده",items.count{!it.paid}.toString());ReportRow("کل مبلغ",money(total));ReportRow("دریافتی",money(paid));ReportRow("بدهی",money(total-paid))}} }
@Composable fun ReportRow(a:String,b:String){Card(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(15.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(a);Text(b,style=MaterialTheme.typography.titleMedium)}}}

@Composable fun EntryDialog(old:Installment?,onDismiss:()->Unit,onSave:(Installment)->Unit){ var c by remember{mutableStateOf(old?.customer?:(""))};var phone by remember{mutableStateOf(old?.phone?:"" )};var p by remember{mutableStateOf(old?.policy?:"" )};var a by remember{mutableStateOf(old?.amount?.toString()?:"" )};var d by remember{mutableStateOf(old?.due?:"" )};
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(old==null)"افزودن قسط" else "ویرایش قسط")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(c,{c=it},label={Text("نام بیمه‌گذار")},singleLine=true);OutlinedTextField(phone,{phone=it},label={Text("شماره تماس")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone));OutlinedTextField(p,{p=it},label={Text("شماره بیمه‌نامه")},singleLine=true);OutlinedTextField(a,{a=it.filter(Char::isDigit)},label={Text("مبلغ (تومان)")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));OutlinedTextField(d,{d=it},label={Text("تاریخ سررسید (مثلاً 1405/06/20)")},singleLine=true)}},confirmButton={Button(enabled=c.isNotBlank()&&p.isNotBlank()&&a.toLongOrNull()!=null,onClick={onSave(Installment(old?.id?:System.currentTimeMillis(),c,phone,p,a.toLong(),d,old?.paid?:false))}){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}

@Composable fun SettingsDialog(store:Store,onDismiss:()->Unit){ var pin by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")};var msg by remember{mutableStateOf("")}; Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){Card(Modifier.fillMaxWidth().padding(22.dp)){Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("تنظیمات امنیتی",style=MaterialTheme.typography.headlineSmall);Text("تغییر رمز ورود برنامه");OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(8)},label={Text("رمز جدید")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));OutlinedTextField(confirm,{confirm=it.filter(Char::isDigit).take(8)},label={Text("تکرار رمز")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));if(msg.isNotBlank())Text(msg);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick=onDismiss){Text("بستن")};Button(onClick={if(pin.length>=4&&pin==confirm){store.setPin(pin);msg="رمز با موفقیت تغییر کرد"}else msg="رمزها یکسان نیستند یا کمتر از ۴ رقم هستند"}){Text("ذخیره رمز")}}}}}}

fun money(v:Long)=NumberFormat.getNumberInstance(Locale("fa","IR")).format(v)+" تومان"
