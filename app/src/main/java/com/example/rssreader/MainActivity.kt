package com.example.rssreader

import android.os.Bundle
import android.util.Log
import android.util.Xml
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rssreader.ui.theme.RSSReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RSSReaderTheme {
                val rssItems = remember { mutableStateListOf<RSSItem>() }
                var isLoading by remember { mutableStateOf(false) }
                var urlInput by remember { mutableStateOf("https://www.un.org/en/rss.xml") } // Пример рабочей RSS

                // CoroutineScope необходим для запуска сетевого запроса по нажатию кнопки
                val coroutineScope = rememberCoroutineScope()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {

                            // Передаем значение, событие изменения и обработчик клика в SearchBox
                            SearchBox(
                                url = urlInput,
                                onUrlChange = { urlInput = it },
                                onGoClick = {
                                    if (urlInput.isNotBlank()) {
                                        isLoading = true
                                        coroutineScope.launch {
                                            val fetchedItems = fetchAndParseRss(urlInput)
                                            rssItems.clear()
                                            rssItems.addAll(fetchedItems)
                                            isLoading = false
                                        }
                                    }
                                }
                            )

                            if (isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(rssItems) { item ->
                                        when (item.type) {
                                            RSSType.TEXT -> RSSItemText(item)
                                            RSSType.VIDEO -> RSSItemVideo(item)
                                            RSSType.IMAGE -> RSSItemImage(item)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Сетевой запрос и парсинг XML (остался без изменений)
suspend fun fetchAndParseRss(urlString: String): List<RSSItem> = withContext(Dispatchers.IO) {
    val items = mutableListOf<RSSItem>()
    try {
        val url = URL(urlString)
        val connection = url.openConnection()
        val inputStream = connection.getInputStream()

        val parser = Xml.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var currentTitle = ""
        var currentDescription = ""
        var insideItem = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName.equals("item", ignoreCase = true)) {
                        insideItem = true
                    } else if (insideItem) {
                        when {
                            tagName.equals("title", ignoreCase = true) -> currentTitle = parser.nextText()
                            tagName.equals("description", ignoreCase = true) -> currentDescription = parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName.equals("item", ignoreCase = true)) {
                        val cleanText = currentDescription.replace(Regex("<[^>]*>"), "").trim()
                        items.add(RSSItem(currentTitle, cleanText, RSSType.TEXT))
                        currentTitle = ""
                        currentDescription = ""
                        insideItem = false
                    }
                }
            }
            eventType = parser.next()
        }
    } catch (e: Exception) {
        Log.e("RSS_READER", "Ошибка загрузки RSS ленты", e)
    }
    return@withContext items
}

@Composable
fun SearchBox(
    url: String,
    onUrlChange: (String) -> Unit, // Исправлено Basin -> Unit
    onGoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically // Исправлено alignment -> verticalAlignment
    ) {
        TextField(
            value = url,
            onValueChange = onUrlChange,
            singleLine = true,
            modifier = Modifier
                .weight(1f) // Занимает всё доступное пространство слева от кнопки
                .padding(end = 8.dp),
            placeholder = { Text("Введите RSS URL...") }
        )
        Button(
            onClick = onGoClick
        ) {
            Text("Go")
        }
    }
}


// Компоненты отрисовки карточек (RSSItemText, RSSItemVideo, RSSItemImage) остаются без изменений
@Composable
fun RSSItemText(rssItem: RSSItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = rssItem.title, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
            Text(text = rssItem.text, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun RSSItemVideo(rssItem: RSSItem) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text(text = rssItem.title, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(12.dp))
        rssItem.media?.let { photo ->
            Image(painter = painterResource(id = photo), contentDescription = "Video", modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun RSSItemImage(rssItem: RSSItem) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text(
            text = rssItem.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(12.dp).clickable { Log.d("djmalone", "Photo tapped") }
        )
        rssItem.media?.let { photo ->
            Image(painter = painterResource(id = photo), contentDescription = "Image", modifier = Modifier.fillMaxWidth())
        }
    }
}
