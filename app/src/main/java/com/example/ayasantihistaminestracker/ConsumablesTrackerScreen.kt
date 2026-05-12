package com.example.ayasantihistaminestracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.edit
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment

class ConsumablesScreenFragment : Fragment(), MainActivity.TitleProvider {
    override fun getTitle(): String = "CT1"
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return ComposeView(requireContext()).apply {
            setContent {
                ConsumablesTrackerScreen(
                    onBack = {
                        val tabs = activity?.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tab_layout)
                        tabs?.getTabAt(0)?.select()
                    }
                )
            }
        }
    }
}

data class ConsumableEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val category: String = "",
    val subCategory: String? = "", 
    val timestamp: Long = 0L,
    val isRecurringMed: Boolean = false,
    val isSavedRecurringMed: Boolean = false,
    val isHiddenFromLog: Boolean = false,
    val relationFlag: Boolean = false,
    val isDeleted: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumablesTrackerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ConsumablesData", Context.MODE_PRIVATE) }
    val pillPrefs = remember { context.getSharedPreferences("PillLogs", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }
    
    var entries by remember { mutableStateOf(listOf<ConsumableEntry>()) }
    var consumableName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var selectedSubCategory by remember { mutableStateOf("") }
    var isRecurringMed by remember { mutableStateOf(false) }
    var selectedTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showSummary by remember { mutableStateOf(false) }
    var showWarningPopup by remember { mutableStateOf<String?>(null) }
    var showOtherDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var otherInput by remember { mutableStateOf("") }
    var editingEntry by remember { mutableStateOf<ConsumableEntry?>(null) }
    
    var recurringMedsList by remember { mutableStateOf(listOf<String>()) }
    var excludeRecurringFromLogs by remember { mutableStateOf(false) }

    val neutralTextFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        cursorColor = Color.LightGray,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedIndicatorColor = Color.Gray,
        unfocusedIndicatorColor = Color.DarkGray,
        focusedPlaceholderColor = Color.Gray,
        unfocusedPlaceholderColor = Color.Gray
    )
    val sdfFull = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US)
    val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    val sdfTime = SimpleDateFormat("hh:mm a", Locale.US)

    // Load entries
    LaunchedEffect(Unit) {
        val json = prefs.getString("entries", "[]")
        val type = object : TypeToken<List<ConsumableEntry>>() {}.type
        val loaded: List<ConsumableEntry> = gson.fromJson(json, type) ?: emptyList()
        entries = loaded.map { 
            it.copy(
                subCategory = it.subCategory ?: "",
                relationFlag = checkRelation(it.timestamp, pillPrefs)
            ) 
        }
        
        val medsJson = prefs.getString("saved_recurring_meds", "[]")
        recurringMedsList = gson.fromJson(medsJson, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        excludeRecurringFromLogs = prefs.getBoolean("exclude_recurring_from_logs", false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CT1", color = Color.White, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { exportData(context, entries) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F111A))
            )
        },
        containerColor = Color(0xFF0F111A)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("What did you consume?", color = Color.LightGray, fontSize = 14.sp)
                        TextField(
                            value = consumableName,
                            onValueChange = { 
                                consumableName = if (it.isNotEmpty()) it.replaceFirstChar { char -> char.uppercase() } else it 
                            },
                            placeholder = { Text("e.g. Coffee, Paracetamol...", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = neutralTextFieldColors
                        )

                        // Quick Selections (Frequency based)
                        val activeEntries = entries.filter { !it.isDeleted && !it.isHiddenFromLog }
                        val quickSelections = remember(activeEntries) {
                            activeEntries.groupingBy { Triple(it.name, it.category, it.subCategory ?: "") }
                                .eachCount()
                                .toList()
                                .sortedByDescending { it.second }
                                .take(8)
                        }

                        if (quickSelections.isNotEmpty()) {
                            Text("Quick Selections", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
                            Text("Frequently selected consumables", color = Color.Gray, fontSize = 10.sp)
                            
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(quickSelections) { (triple, _) ->
                                    val (name, cat, subCat) = triple
                                    InputChip(
                                        selected = false,
                                        onClick = {
                                            val entry = ConsumableEntry(
                                                name = name,
                                                category = cat,
                                                subCategory = subCat,
                                                timestamp = selectedTimestamp,
                                                isRecurringMed = cat == "Medication",
                                                isSavedRecurringMed = recurringMedsList.contains(name),
                                                isHiddenFromLog = (cat == "Medication") && excludeRecurringFromLogs,
                                                relationFlag = checkRelation(selectedTimestamp, pillPrefs)
                                            )
                                            entries = (listOf(entry) + entries).sortedByDescending { it.timestamp }
                                            saveEntries(prefs, gson, entries)
                                            Toast.makeText(context, "Logged: $name", Toast.LENGTH_SHORT).show()
                                        },
                                        label = { Text(name, fontSize = 11.sp) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Remove",
                                                modifier = Modifier.size(14.dp).clickable {
                                                    entries = entries.map { if (it.name == name) it.copy(isDeleted = true) else it }
                                                    saveEntries(prefs, gson, entries)
                                                },
                                                tint = Color.Gray
                                            )
                                        },
                                        colors = InputChipDefaults.inputChipColors(
                                            containerColor = Color(0xFF2C2C2C),
                                            labelColor = Color.White
                                        ),
                                        border = null
                                    )
                                }
                            }
                        }

                        if (selectedCategory == "Medication") {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isRecurringMed,
                                        onCheckedChange = { isRecurringMed = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF42A5F5))
                                    )
                                    Text("Recurring Medication", color = Color.White, fontSize = 14.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Switch(
                                        checked = excludeRecurringFromLogs,
                                        onCheckedChange = { 
                                            excludeRecurringFromLogs = it
                                            prefs.edit().putBoolean("exclude_recurring_from_logs", it).apply()
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF42A5F5))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Exclude Recurring Meds From Logs", color = Color.White, fontSize = 13.sp)
                                        Text("Avoid cluttering your history feed", color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = selectedTimestamp
                            Text(
                                text = "${sdfDate.format(cal.time)} — ${sdfTime.format(cal.time)}",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            IconButton(onClick = {
                                DatePickerDialog(context, { _, y, m, d ->
                                    val timeCal = Calendar.getInstance()
                                    timeCal.timeInMillis = selectedTimestamp
                                    TimePickerDialog(context, { _, hr, min ->
                                        val newCal = Calendar.getInstance()
                                        newCal.set(y, m, d, hr, min)
                                        selectedTimestamp = newCal.timeInMillis
                                    }, timeCal.get(Calendar.HOUR_OF_DAY), timeCal.get(Calendar.MINUTE), false).show()
                                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                            }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Pick Date", tint = Color(0xFF42A5F5))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Choose from list", color = Color(0xFF82B1FF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TriggerSelectionSection(
                            entries = entries,
                            recurringMeds = recurringMedsList,
                            onAddRecurring = { newMed ->
                                if (!recurringMedsList.contains(newMed)) {
                                    recurringMedsList = recurringMedsList + newMed
                                    prefs.edit().putString("saved_recurring_meds", gson.toJson(recurringMedsList)).apply()
                                }
                            },
                            onSelect = { item, category, subCategory ->
                                if (item == "Other") {
                                    showOtherDialog = category to subCategory
                                } else {
                                    consumableName = item
                                    selectedSubCategory = subCategory
                                    selectedCategory = when {
                                        category.contains("Medication") -> "Medication"
                                        category.contains("Food") -> "Food"
                                        category.contains("Drink") -> "Drinks"
                                        category.contains("Beverage") -> "Drinks"
                                        category.contains("Smoke") -> "Smoke"
                                        else -> "Other"
                                    }
                                    if (selectedCategory == "Medication") {
                                        isRecurringMed = recurringMedsList.contains(item)
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (editingEntry != null) {
                                Button(
                                    onClick = {
                                        editingEntry = null
                                        consumableName = ""
                                        isRecurringMed = false
                                        selectedTimestamp = System.currentTimeMillis()
                                    },
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                ) {
                                    Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Button(
                                onClick = {
                                    if (consumableName.isNotBlank()) {
                                        val currentEditing = editingEntry
                                        if (currentEditing != null) {
                                            // Update existing entry
                                            entries = entries.map { 
                                                if (it.id == currentEditing.id) {
                                                    it.copy(
                                                        name = consumableName,
                                                        category = selectedCategory,
                                                        subCategory = selectedSubCategory,
                                                        timestamp = selectedTimestamp,
                                                        isRecurringMed = isRecurringMed,
                                                        isHiddenFromLog = isRecurringMed && excludeRecurringFromLogs,
                                                        relationFlag = checkRelation(selectedTimestamp, pillPrefs)
                                                    )
                                                } else it
                                            }
                                            editingEntry = null
                                            Toast.makeText(context, "Entry updated!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            // Create new entry
                                            val entry = ConsumableEntry(
                                                name = consumableName,
                                                category = selectedCategory,
                                                subCategory = selectedSubCategory,
                                                timestamp = selectedTimestamp,
                                                isRecurringMed = isRecurringMed,
                                                isHiddenFromLog = isRecurringMed && excludeRecurringFromLogs,
                                                relationFlag = checkRelation(selectedTimestamp, pillPrefs)
                                            )
                                            entries = (listOf(entry) + entries).sortedByDescending { it.timestamp }
                                            Toast.makeText(context, "Entry added!", Toast.LENGTH_SHORT).show()
                                        }
                                        saveEntries(prefs, gson, entries)
                                        checkWarningPatterns(consumableName, entries) { warningName ->
                                            showWarningPopup = warningName
                                        }
                                        consumableName = ""
                                        selectedSubCategory = ""
                                        isRecurringMed = false
                                        selectedTimestamp = System.currentTimeMillis()
                                    }
                                },
                                modifier = Modifier.weight(2f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (editingEntry != null) Color(0xFF82B1FF) else Color(0xFF42A5F5))
                            ) {
                                Text(if (editingEntry != null) "Update Entry" else "Log Consumable", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Logs", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showSummary = true }) {
                        Text("View Summary", color = Color(0xFF42A5F5))
                    }
                }
            }

            val visibleEntries = entries.filter { !it.isDeleted && !it.isHiddenFromLog }
            if (visibleEntries.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No logs yet. Start by adding what you consume.", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                items(visibleEntries, key = { it.id }) { entry ->
                    HistoryCard(
                        entry = entry,
                        sdfFull = sdfFull,
                        onDelete = {
                            entries = entries.map { if (it.id == entry.id) it.copy(isDeleted = true) else it }
                            saveEntries(prefs, gson, entries)
                        },
                        onEdit = {
                            editingEntry = entry
                            consumableName = entry.name
                            selectedCategory = entry.category
                            selectedSubCategory = entry.subCategory ?: ""
                            selectedTimestamp = entry.timestamp
                            isRecurringMed = entry.isRecurringMed
                            Toast.makeText(context, "Editing ${entry.name}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    if (showSummary) {
        SummaryModal(entries.filter { !it.isDeleted }) { showSummary = false }
    }

    showOtherDialog?.let { (category, subCategory) ->
        AlertDialog(
            onDismissRequest = { showOtherDialog = null; otherInput = "" },
            title = { Text("Enter Other ${if (subCategory.isNotEmpty()) subCategory else category}", color = Color.White) },
            text = {
                Column {
                    // Quick selections for what user entered manually in this category/subcategory
                    val activeEntries = entries.filter { 
                        !it.isDeleted && it.category == category && it.subCategory == subCategory 
                    }
                    val categoryHistory = remember(activeEntries) {
                        activeEntries.groupingBy { it.name }.eachCount()
                            .toList().sortedByDescending { it.second }.take(5)
                    }
                    
                    if (categoryHistory.isNotEmpty()) {
                        Text("Past entries:", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(categoryHistory) { (name, _) ->
                                SuggestionChip(
                                    onClick = { otherInput = name },
                                    label = { Text(name, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                    
                    TextField(
                        value = otherInput,
                        onValueChange = { 
                            otherInput = if (it.isNotEmpty()) it.replaceFirstChar { char -> char.uppercase() } else it 
                        },
                        modifier = Modifier.padding(top = 8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color.LightGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Gray,
                            unfocusedIndicatorColor = Color.DarkGray
                        )
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (otherInput.isNotBlank()) {
                        consumableName = otherInput
                        selectedSubCategory = subCategory
                        selectedCategory = when {
                            category.contains("Medication") -> "Medication"
                            category.contains("Food") -> "Food"
                            category.contains("Drink") -> "Drinks"
                            category.contains("Beverage") -> "Drinks"
                            category.contains("Smoke") -> "Smoke"
                            else -> "Other"
                        }
                        showOtherDialog = null
                        otherInput = ""
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showOtherDialog = null; otherInput = "" }) { Text("Cancel") } },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    showWarningPopup?.let { name ->
        WarningDialog(name) { showWarningPopup = null }
    }
}

@Composable
fun TriggerSelectionSection(
    entries: List<ConsumableEntry>,
    recurringMeds: List<String>,
    onAddRecurring: (String) -> Unit,
    onSelect: (String, String, String) -> Unit
) {
    val activeEntries = entries.filter { !it.isDeleted }
    
    val foodSubcategories = listOf(
        "Fruit" to listOf("Apple", "Banana", "Orange", "Strawberry", "Mango", "Watermelon", "Grapes", "Pineapple", "Peach", "Kiwi", "Lemon", "Avocado"),
        "Vegetable" to listOf("Tomato", "Cucumber", "Lettuce", "Onion", "Garlic", "Potato", "Carrot", "Spinach", "Broccoli", "Eggplant", "Bell Pepper", "Zucchini"),
        "Beans" to listOf("Lentils", "Chickpeas", "Kidney Beans", "Black Beans", "White Beans", "Fava Beans", "Green Beans"),
        "Red Meat" to listOf("Beef", "Lamb", "Veal", "Goat"),
        "Poultry" to listOf("Chicken", "Turkey", "Duck"),
        "Pasta" to listOf("White Pasta", "Whole Wheat Pasta", "Instant Noodles", "Rice Noodles"),
        "Oat" to listOf("Oatmeal", "Overnight Oats", "Granola Oats"),
        "Spices" to listOf("Black Pepper", "Chili", "Paprika", "Cinnamon", "Turmeric", "Curry", "Cumin", "Ginger", "Garlic Powder", "Oregano"),
        "Bread" to listOf("White Bread", "Brown Bread", "Whole Wheat Bread", "Toast", "Pita Bread", "Sourdough", "Croissant"),
        "Honey" to listOf("Natural Honey", "Processed Honey"),
        "Dried Fruit / Vegetables" to listOf("Raisins", "Dried Apricot", "Dates", "Prunes", "Dried Tomato", "Banana Chips"),
        "Fish & Shellfish" to listOf("Tuna", "Salmon", "Sardines", "Shrimp", "Crab", "Lobster", "Tilapia", "Mackerel"),
        "Nuts" to listOf("Almonds", "Walnuts", "Cashews", "Pistachios", "Peanuts", "Hazelnuts", "Pecans"),
        "General Food" to listOf("Spicy food", "Fast food", "Dairy", "Aged & Processed Cheeses", "Eggs", "Chocolate", "Processed food", "High histamine foods")
    )

    val drinkSubcategories = listOf(
        "Herbal Tea" to listOf("Chamomile", "Peppermint", "Green Tea", "Hibiscus", "Anise", "Ginger Tea", "Cinnamon Tea"),
        "Alcohol" to listOf("Beer", "Wine", "Whiskey", "Vodka", "Rum", "Gin", "Cocktail"),
        "Fresh Juice" to listOf("Orange Juice", "Mango Juice", "Strawberry Juice", "Watermelon Juice", "Lemon Juice", "Carrot Juice"),
        "Canned / Boxed Juice" to listOf("Apple Juice Box", "Mixed Fruit Juice", "Mango Nectar", "Grape Juice", "Energy Juice Drink"),
        "General Drinks" to listOf("Caffeine", "Energy drinks", "Soda", "Coffee", "Black Tea")
    )

    val medicationSubcategories = listOf(
        "Medications" to listOf("Antibiotics", "NSAIDs", "Paracetamol", "Ibuprofen", "Steroids", "Vitamins", "Herbal supplements", "Protein supplements")
    )

    val smokeOptions = listOf("Cigarettes", "Vape", "Shisha", "Cigar", "Cannabis", "Passive Smoke Exposure")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Food Category
        CategoryWithSubcategories("Food", foodSubcategories, onSelect)

        // Drinks Category
        CategoryWithSubcategories("Drinks", drinkSubcategories, onSelect)

        // Medication Category
        var medExpanded by remember { mutableStateOf(false) }
        var showAddRecurring by remember { mutableStateOf(false) }
        var newRecurringName by remember { mutableStateOf("") }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { medExpanded = !medExpanded }.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Medication", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = if (medExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            AnimatedVisibility(visible = medExpanded) {
                Column(modifier = Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Recurring Meds Section
                    Text("Recurring Meds", color = Color.Gray.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recurringMeds) { med ->
                            SuggestionChip(
                                onClick = { onSelect(med, "Medication", "Recurring") },
                                label = { Text(med, fontSize = 10.sp) }
                            )
                        }
                        item {
                            SuggestionChip(
                                onClick = { showAddRecurring = true },
                                label = { Text("+ Add New Recurring", fontSize = 10.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF42A5F5).copy(alpha = 0.2f), labelColor = Color(0xFF42A5F5))
                            )
                        }
                    }

                    if (showAddRecurring) {
                        AlertDialog(
                            onDismissRequest = { showAddRecurring = false; newRecurringName = "" },
                            title = { Text("Add Recurring Medication", color = Color.White) },
                            text = {
                                TextField(
                                    value = newRecurringName,
                                    onValueChange = { 
                                        newRecurringName = if (it.isNotEmpty()) it.replaceFirstChar { char -> char.uppercase() } else it 
                                    },
                                    placeholder = { Text("Enter medication name") },
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                            },
                            confirmButton = {
                                Button(onClick = {
                                    if (newRecurringName.isNotBlank()) {
                                        onAddRecurring(newRecurringName)
                                        showAddRecurring = false
                                        newRecurringName = ""
                                    }
                                }) { Text("Save") }
                            },
                            dismissButton = { TextButton(onClick = { showAddRecurring = false; newRecurringName = "" }) { Text("Cancel") } },
                            containerColor = Color(0xFF1E1E1E)
                        )
                    }

                    // Other Med categories
                    medicationSubcategories.forEach { (subName, items) ->
                        SubCategoryRow(subName, items, "Medication", onSelect)
                    }
                }
            }
        }

        // Smoke Category
        var smokeExpanded by remember { mutableStateOf(false) }
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { smokeExpanded = !smokeExpanded }.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Smoke", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = if (smokeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
            AnimatedVisibility(visible = smokeExpanded) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(smokeOptions) { item ->
                        SuggestionChip(
                            onClick = { onSelect(item, "Smoke", "") },
                            label = { Text(item, fontSize = 11.sp) }
                        )
                    }
                    item {
                        SuggestionChip(
                            onClick = { onSelect("Other", "Smoke", "") },
                            label = { Text("Other", fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF42A5F5).copy(alpha = 0.2f), labelColor = Color(0xFF42A5F5))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryWithSubcategories(
    catName: String,
    subcategories: List<Pair<String, List<String>>>,
    onSelect: (String, String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(catName, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
        
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subcategories.forEach { (subName, items) ->
                    SubCategoryRow(subName, items, catName, onSelect)
                }
            }
        }
    }
}

@Composable
fun SubCategoryRow(
    subName: String,
    items: List<String>,
    catName: String,
    onSelect: (String, String, String) -> Unit
) {
    var subExpanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { subExpanded = !subExpanded }.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(subName, color = Color.Gray.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Icon(
                imageVector = if (subExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(14.dp)
            )
        }
        AnimatedVisibility(visible = subExpanded) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    SuggestionChip(
                        onClick = { onSelect(item, catName, subName) },
                        label = { Text(item, fontSize = 10.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFF2C2C2C),
                            labelColor = Color.White
                        )
                    )
                }
                item {
                    SuggestionChip(
                        onClick = { onSelect("Other", catName, subName) },
                        label = { Text("Other", fontSize = 10.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFF42A5F5).copy(alpha = 0.2f),
                            labelColor = Color(0xFF42A5F5)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryCard(entry: ConsumableEntry, sdfFull: SimpleDateFormat, onDelete: () -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161822)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (entry.isRecurringMed) {
                        Surface(
                            color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Recurring", color = Color(0xFF81C784), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
                val subCategoryText = entry.subCategory ?: ""
                Text(entry.category + (if (subCategoryText.isNotEmpty()) " > $subCategoryText" else ""), color = Color.Gray, fontSize = 12.sp)
                Text(sdfFull.format(Date(entry.timestamp)), color = Color.DarkGray, fontSize = 11.sp)
            }
            if (entry.relationFlag) {
                Text("⚠️ Relation", color = Color(0xFFFF7043), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
            }
            
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp).padding(end = 4.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Clear, contentDescription = "Delete", tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun SummaryModal(entries: List<ConsumableEntry>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFF42A5F5)) } },
        title = { Text("Log Summary", color = Color.White) },
        containerColor = Color(0xFF1E1E1E),
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                val grouped = entries.filter { !it.isHiddenFromLog }.groupBy { it.category }
                grouped.forEach { (cat, list) ->
                    item {
                        SummarySection(cat, list)
                    }
                }
            }
        }
    )
}

@Composable
fun SummarySection(title: String, list: List<ConsumableEntry>) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, color = Color(0xFF42A5F5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        
        // Group by subcategory for Food to provide better intelligence
        if (title == "Food") {
            val subGrouped = list.groupBy { it.subCategory ?: "" }
            subGrouped.forEach { (subCat, subList) ->
                if (subCat.isNotEmpty()) {
                    Text("  $subCat:", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
                }
                val counts = subList.groupingBy { it.name }.eachCount()
                counts.forEach { (name, count) ->
                    Text("    • $name: $count times", color = Color.LightGray, fontSize = 13.sp)
                }
            }
        } else {
            val counts = list.groupingBy { it.name }.eachCount()
            counts.forEach { (name, count) ->
                Text("  • $name: $count times", color = Color.LightGray, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun WarningDialog(consumable: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))) { Text("I understand", color = Color.White) } },
        title = { Text("Pattern Alert", color = Color.White) },
        text = { Text("'$consumable' has been frequently logged within 24h of a flare-up. Consider discussing this with your specialist.", color = Color.LightGray) },
        containerColor = Color(0xFF2C1010)
    )
}

fun checkRelation(timestamp: Long, pillPrefs: SharedPreferences): Boolean {
    val json = pillPrefs.getString("pills", "[]")
    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
    val pills: List<Map<String, Any>> = Gson().fromJson(json, type)
    return pills.any { pill ->
        val pillTs = (pill["timestamp"] as? Double)?.toLong() ?: 0L
        kotlin.math.abs(pillTs - timestamp) < 24 * 60 * 60 * 1000L
    }
}

fun checkWarningPatterns(name: String, allEntries: List<ConsumableEntry>, onWarning: (String) -> Unit) {
    val activeEntries = allEntries.filter { !it.isDeleted }
    val count = activeEntries.count { it.name.equalsIgnoreCase(name) && it.relationFlag }
    if (count >= 3) {
        onWarning(name)
    }
}

fun String.equalsIgnoreCase(other: String) = this.equals(other, ignoreCase = true)

fun saveEntries(prefs: SharedPreferences, gson: Gson, entries: List<ConsumableEntry>) {
    prefs.edit { putString("entries", gson.toJson(entries)) }
}

fun exportData(context: Context, entries: List<ConsumableEntry>) {
    val activeEntries = entries.filter { !it.isDeleted }
    val sb = StringBuilder("Name,Category,SubCategory,Timestamp,IsRecurringMed,IsHidden\n")
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    activeEntries.forEach {
        sb.append("${it.name},${it.category},${it.subCategory ?: ""},${sdf.format(Date(it.timestamp))},${it.isRecurringMed},${it.isHiddenFromLog}\n")
    }
    
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "CT1 Export")
        putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share Export"))
}
