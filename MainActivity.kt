package ir.bimeh.installments

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

data class Installment(val id: Long, val customer: String, val policy: String, val amount: Long, val due: String, val paid: Boolean)

class Store(context: Context) {
    private val p = context.getSharedPreferences("bimeh", Context.MODE_PRIVATE)
    fun load(): List<Installment> = runCatching {
        val a = JSONArray(p.getString("items", "[]"))
        (0 until a.length()).map { i -> val o=a.getJSONObject(i); Installment(o.getLong("id"),o.getString("customer"),o.getString("policy"),o.getLong("amount"),o.getString("due"),o.getBoolean("paid")) }
    }.getOrDefault(emptyList())
    fun save(xs: List<Installment>) { val a=JSONArray(); xs.forEach { x -> a.put(JSONObject().apply { put("id",x.id);put("customer",x.customer);put("policy",x.policy);put("amount",x.amount);put("due",x.due);put("paid",x.paid) }) }; p.edit().putString("items",a.toString()).apply() }
}

class MainActivity : ComponentActivity() { override fun onCreate(b: Bundle?) { super.onCreate(b); setContent { App(Store(this)) } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App(store: Store) {
    var xs by remember { mutableStateOf(store.load()) }; var tab by remember { mutableIntStateOf(0) }; var add by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF176B87),secondary=Color(0xFF64CCC5))) {
        Scaffold(topBar={ TopAppBar(title={Text("مدیریت اقساط بیمه",fontWeight=FontWeight.Bold)},actions={IconButton({add=true}){Icon(Icons.Default.Add,null)}})},
            bottomBar={NavigationBar{listOf("داشبورد" to Icons.Default.Home,"اقساط" to Icons.Default.CalendarMonth,"بیمه‌گذاران" to Icons.Default.People,"گزارش" to Icons.Default.Assessment).forEachIndexed{i,(t,ic)->NavigationBarItem(tab==i,{tab=i},{Icon(ic,null)},label={Text(t)})}}}) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) { when(tab){0->Dashboard(xs){add=true};1->Installments(xs){id->xs=xs.map{if(it.id==id)it.copy(paid=true)else it};store.save(xs)};2->Customers(xs);3->Reports(xs)} }
        }
        if(add) AddDialog({add=false}){c,p,a,d->xs=listOf(Installment(System.currentTimeMillis(),c,p,a,d,false))+xs;store.save(xs);add=false}
    }
}

@Composable fun Dashboard(xs:List<Installment>, add:()->Unit){val total=xs.sumOf{it.amount};val paid=xs.filter{it.paid}.sumOf{it.amount};Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("خلاصه وضعیت",fontSize=24.sp,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){Stat("کل",money(total),Modifier.weight(1f));Stat("دریافتی",money(paid),Modifier.weight(1f))};Stat("مانده بدهی",money(total-paid),Modifier.fillMaxWidth());Text("آخرین اقساط",fontWeight=FontWeight.Bold,fontSize=18.sp);if(xs.isEmpty())Button(add,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("افزودن اولین قسط")}else xs.take(5).forEach{CardItem(it,{})}}}
@Composable fun Installments(xs:List<Installment>,paid:(Long)->Unit){var q by remember{mutableStateOf("")};val ys=xs.filter{it.customer.contains(q,true)||it.policy.contains(q,true)};Column(Modifier.padding(16.dp)){OutlinedTextField(q,{q=it},modifier=Modifier.fillMaxWidth(),label={Text("جستجو نام یا شماره بیمه‌نامه")},leadingIcon={Icon(Icons.Default.Search,null)});Spacer(Modifier.height(10.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(ys,key={it.id}){CardItem(it){paid(it.id)}}}}}
@Composable fun Customers(xs:List<Installment>){LazyColumn(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(xs.groupBy{it.customer}.entries.toList()){(n,r)->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(n,fontSize=18.sp,fontWeight=FontWeight.Bold);Text("اقساط: ${r.size}");Text("بدهی: ${money(r.filterNot{it.paid}.sumOf{it.amount})}")}}}}}
@Composable fun Reports(xs:List<Installment>){val t=xs.sumOf{it.amount};val p=xs.filter{it.paid}.sumOf{it.amount};Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("گزارش مالی",fontSize=24.sp,fontWeight=FontWeight.Bold);listOf("تعداد اقساط" to xs.size.toString(),"مبلغ کل" to money(t),"دریافتی" to money(p),"بدهی" to money(t-p),"پرداخت‌شده" to xs.count{it.paid}.toString(),"پرداخت‌نشده" to xs.count{!it.paid}.toString()).forEach{(a,b)->Card(Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(a);Text(b,fontWeight=FontWeight.Bold)}}}}}
@Composable fun Stat(a:String,b:String,m:Modifier){Card(m){Column(Modifier.padding(16.dp)){Text(a);Text(b,fontSize=19.sp,fontWeight=FontWeight.Bold)}}}
@Composable fun CardItem(x:Installment,onPaid:()->Unit){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(x.customer,fontSize=18.sp,fontWeight=FontWeight.Bold);Text(if(x.paid)"پرداخت شد" else "بدهکار",fontWeight=FontWeight.Bold)};Text("بیمه‌نامه: ${x.policy}");Text("سررسید: ${x.due}");Text("مبلغ: ${money(x.amount)}");if(!x.paid)Button(onPaid,modifier=Modifier.align(Alignment.End)){Text("ثبت پرداخت")}}}}
@Composable fun AddDialog(close:()->Unit,save:(String,String,Long,String)->Unit){var c by remember{mutableStateOf("")};var p by remember{mutableStateOf("")};var a by remember{mutableStateOf("")};var d by remember{mutableStateOf("")};AlertDialog(onDismissRequest=close,title={Text("ثبت قسط جدید")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){OutlinedTextField(c,{c=it},label={Text("نام بیمه‌گذار")});OutlinedTextField(p,{p=it},label={Text("شماره بیمه‌نامه")});OutlinedTextField(a,{a=it.filter(Char::isDigit)},label={Text("مبلغ قسط (تومان)")});OutlinedTextField(d,{d=it},label={Text("تاریخ سررسید")})}},confirmButton={Button(enabled=c.isNotBlank()&&a.isNotBlank(),onClick={save(c,p,a.toLongOrNull()?:0,d)}){Text("ذخیره")}},dismissButton={TextButton(close){Text("انصراف")}})}
fun money(v:Long)=NumberFormat.getNumberInstance(Locale.US).format(v)+" تومان"
