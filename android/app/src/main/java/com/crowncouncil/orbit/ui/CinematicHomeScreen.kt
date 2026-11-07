package com.crowncouncil.orbit.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private data class HeroPlace(val name:String,val region:String,val line:String,val image:String)
private val heroPlaces=listOf(
 HeroPlace("Sajek Valley","Rangamati · Chattogram","Clouds at your doorstep","https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=1200&q=85"),
 HeroPlace("Cox's Bazar","Cox's Bazar · Chattogram","A horizon that never ends","https://images.unsplash.com/photo-1500375592092-40eb2168fd21?w=1200&q=85"),
 HeroPlace("Jaflong","Sylhet","Where rivers meet the hills","https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=1200&q=85"),
 HeroPlace("Ratargul","Sylhet","Into the quiet green","https://images.unsplash.com/photo-1448375240586-882707db888b?w=1200&q=85")
)

@Composable
fun CinematicHomeScreen(vm:OrbitViewModel,onPlan:()->Unit,onExplore:()->Unit,onGuide:()->Unit,onMap:()->Unit) {
 val visited by vm.visitedIds.collectAsState()
 val destinations by vm.destinations.collectAsState()
 val user by vm.user.collectAsState()
 var index by remember{mutableIntStateOf(0)}
 LaunchedEffect(Unit){while(true){delay(5000);index=(index+1)%heroPlaces.size}}
 val transition=rememberInfiniteTransition(label="vehicle")
 val travel by transition.animateFloat(0f,1f,infiniteRepeatable(tween(6800,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="travel")
 val districtCount=destinations.filter{it.id in visited}.map{it.district}.distinct().size
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
  item{Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   Column{Text("O R B I T",style=MaterialTheme.typography.titleLarge,letterSpacing=4.sp);Text("BANGLADESH, IN MOTION",color=MaterialTheme.colorScheme.primary,fontSize=9.sp,letterSpacing=2.sp,fontWeight=FontWeight.Bold)}
   Text("✳",fontSize=25.sp,color=MaterialTheme.colorScheme.primary)
  }}
  item{Box(Modifier.fillMaxWidth().padding(horizontal=16.dp).height(430.dp).clip(RoundedCornerShape(28.dp))){
   Crossfade(index,label="hero-photo"){i->AsyncImage(model=heroPlaces[i].image,contentDescription=heroPlaces[i].name,modifier=Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)}
   Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.12f),Color.Black.copy(alpha=.88f)))))
   Column(Modifier.align(Alignment.BottomStart).padding(23.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("BANGLADESH · DESTINATION SPOTLIGHT",color=Color.White.copy(alpha=.8f),fontSize=9.sp,letterSpacing=1.4.sp)
    Text(heroPlaces[index].name,color=Color.White,style=MaterialTheme.typography.headlineMedium,fontSize=36.sp)
    Text(heroPlaces[index].line,color=Color.White.copy(alpha=.9f),fontSize=14.sp)
    Text(heroPlaces[index].region,color=Color.White.copy(alpha=.75f),fontSize=12.sp)
    Button(onClick=onExplore,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFD5B979),contentColor=Color(0xFF171711))){Text("Discover this place");Icon(Icons.Default.Search,null,Modifier.padding(start=6.dp))}
   }
   Column(Modifier.align(Alignment.BottomEnd).padding(end=10.dp,bottom=10.dp).offset(x=(travel*34f).dp),horizontalAlignment=Alignment.End) {
    Text("BANGLADESH RAIL · LIVE JOURNEY",color=Color.White.copy(alpha=.86f),fontSize=8.sp,letterSpacing=1.sp)
    Canvas(Modifier.size(width=158.dp,height=48.dp)) {
     drawRoundRect(Color.White.copy(alpha=.25f),Offset(3f,8f),Size(size.width-8f,28f),CornerRadius(10f,10f))
     drawRoundRect(Color.White.copy(alpha=.58f),Offset(10f,13f),Size(size.width-25f,17f),CornerRadius(5f,5f))
     for(i in 0..4) drawRoundRect(Color(0xFF34443D).copy(alpha=.72f),Offset(16f+i*25f,16f),Size(18f,10f),CornerRadius(2f,2f))
     drawRoundRect(Color(0xFFD5B979).copy(alpha=.9f),Offset(size.width-20f,18f),Size(8f,8f),CornerRadius(3f,3f))
     drawCircle(Color(0xFF222522),5f,Offset(34f,38f));drawCircle(Color(0xFF222522),5f,Offset(size.width-37f,38f))
     drawCircle(Color.White.copy(alpha=.75f),2f,Offset(34f,38f));drawCircle(Color.White.copy(alpha=.75f),2f,Offset(size.width-37f,38f))
    }
   }
   Row(Modifier.align(Alignment.TopEnd).padding(16.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)){heroPlaces.indices.forEach{i->Box(Modifier.size(if(i==index)18.dp else 6.dp,6.dp).clip(CircleShape).background(if(i==index)Color.White else Color.White.copy(alpha=.55f)))}}
  }}
  item{Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(9.dp)){
   ActionTile("Plan a trip","Create itinerary",Icons.Default.Create,onPlan,Modifier.weight(1f))
   ActionTile("Ask ORBIT","AI travel guide",Icons.Default.Face,onGuide,Modifier.weight(1f))
   ActionTile("Explore map","Bangladesh",Icons.Default.Place,onMap,Modifier.weight(1f))
  }}
  item{Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   Column{Text("Welcome back, ${user?.displayName?.substringBefore(" ") ?: "Traveller"}",style=MaterialTheme.typography.titleLarge);Text("Your next story starts somewhere.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)}
   Column(horizontalAlignment=Alignment.End){Text("$districtCount / 64",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Text("districts explored",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  }}
  item{Column(Modifier.padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Find your kind of escape",style=MaterialTheme.typography.titleLarge);Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("Beach","Hills","Nature","Heritage").forEach{OutlinedButton(onClick=onExplore,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=3.dp)){Text(it,fontSize=11.sp)}}}}}
  item{Column(Modifier.padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Places worth the journey",style=MaterialTheme.typography.titleLarge);Text("See all →",color=MaterialTheme.colorScheme.primary,modifier=Modifier.clickable(onClick=onExplore))}
   heroPlaces.drop(1).forEach{place->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick=onExplore).padding(9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
    AsyncImage(place.image,place.name,Modifier.size(82.dp).clip(RoundedCornerShape(12.dp)),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
    Column(Modifier.weight(1f)){Text(place.name,fontWeight=FontWeight.SemiBold);Text(place.region,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(place.line,fontSize=11.sp,color=MaterialTheme.colorScheme.primary)}
    Text("↗",color=MaterialTheme.colorScheme.primary,fontSize=20.sp)
   }}
  }}
 }
}
@Composable private fun ActionTile(title:String,sub:String,icon:androidx.compose.ui.graphics.vector.ImageVector,click:()->Unit,modifier:Modifier){
 Card(modifier.clickable(onClick=click),shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){
  Column(Modifier.padding(11.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(title,fontWeight=FontWeight.Bold,fontSize=12.sp);Text(sub,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
 }
}