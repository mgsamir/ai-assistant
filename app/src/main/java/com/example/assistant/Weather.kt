import requests

def get_smart_weather(user_query, latitude=10.5833, longitude=92.5167):
    # ओपन-मेटियो (Open-Meteo) फ्री वेदर API से लाइव डेटा फेच करना (GPS कोऑर्डिनेट्स के आधार पर)
    api_url = f"https://api.open-meteo.com/v1/forecast?latitude={latitude}&longitude={longitude}&current_weather=true"
    
    response = requests.get(api_url)
    if response.status_code != 200:
        return "क्षमा करें, अभी मौसम की जानकारी नहीं मिल पा रही है।"
    
    data = response.json()
    current = data.get("current_weather", {})
    temp = current.get("temperature", 28) # तापमान (Celsius)
    windspeed = current.get("windspeed", 10) # हवा की गति (km/h)
    weathercode = current.get("weathercode", 0) # मौसम का कोड
    
    # यूजर के सवाल को समझना (Query Intent Checking)
    query = user_query.lower()
    
    # यदि यूजर ने तापमान या डिग्री से जुड़ा सवाल पूछा है
    if "टेम्परेचर" in query or "temperature" in query or "डिग्री" in query or "गर्मी" in query or "ठंडा" in query:
        return f"यहाँ का अभी का तापमान लगभग {temp}°C है, और हवा की गति {windspeed} किलोमीटर प्रति घंटा है।"
    
    # यदि यूजर ने सामान्य मौसम पूछा है (बारिश, धूप, हवा, तूफान या आम हाल)
    else:
        # मौसम कोड के आधार पर स्थिति तय करना
        if weathercode >= 51 and weathercode <= 67:
            return "आज बारिश होने की संभावना है या बारिश हो रही है, अपना ध्यान रखें।"
        elif weathercode >= 95:
            return "आज मौसम खराब है, तेज हवा या तूफान चल रहा है।"
        elif temp > 32:
            return "आज अच्छी खासी धूप है और काफी गर्मी है।"
        else:
            return "आज मौसम बिल्कुल साफ है, सुहाना दिन है और धूप खिली हुई है।"

# --- उदाहरण के लिए टेस्टिंग ---
# यदि आप पूछें: "आज अंडमान/हटवे का मौसम कैसा है?"
# आउटपुट आएगा: "आज मौसम बिल्कुल साफ है..." या "आज बारिश होने की संभावना है..."

# यदि आप पूछें: "यहाँ का टेम्परेचर कितना है?"
# आउटपुट आएगा: "यहाँ का अभी का तापमान लगभग 29°C है..."
