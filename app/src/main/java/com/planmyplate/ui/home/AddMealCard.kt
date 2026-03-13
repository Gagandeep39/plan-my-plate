package com.planmyplate.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AddMealCard(
    isFirst: Boolean = false,
    onAddClick: () -> Unit
) {
    val dotCenterY = 26.dp
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            
            // Line part above
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(dotCenterY)
                        .background(lineColor)
                )
            }
            
            // Subtle dot for "Add"
            Box(
                modifier = Modifier
                    .padding(top = dotCenterY - 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Surface(
                onClick = onAddClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Meal",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // Removed bottom spacer to eliminate extra space after Add button
        }
    }
}
