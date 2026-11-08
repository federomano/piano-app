package com.federomano.pianoapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.federomano.pianoapp.score.ExampleScore
import com.federomano.pianoapp.score.ExampleScores

/** Menú de inicio: lista de partituras de ejemplo. Tocar una la abre. */
@Composable
fun HomeMenu(onOpen: (ExampleScore) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(PaperColor), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp),
        ) {
            item {
                Text("Elegí una partitura", color = InkColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            items(ExampleScores.all, key = { it.id }) { example ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(InkColor.copy(alpha = 0.07f))
                        .clickable { onOpen(example) }
                        .padding(16.dp),
                ) {
                    Text(example.title, color = InkColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(example.description, color = InkColor.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
