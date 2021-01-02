class Display {
	constructor(table, sortBy = "Player", descending = false){
		this.table = table
		this.sortBy = sortBy
		this.descending = descending
	}
	
	init(data){
		// Create header of objectives
		let trHeader = document.createElement("tr")
		this.appendTh(trHeader, "Player").setAttribute("colspan", 2)
		this.table.append(trHeader)
		for(const objective of data.scoreboard.objectives){
			this.appendTh(trHeader, objective)
		}
		
		// Create rows of (empty) entries
		for(const entry of data.scoreboard.entries){
			let tr = document.createElement("tr")
			tr.setAttribute("entry", Display.quoteEscape(entry))
			
			// Append skin image
			let img = this.appendElement(tr, "td")
			this.appendImg(img, "https://www.mc-heads.net/avatar/" + entry + ".png")
			img.classList.add("sticky")
			img.setAttribute("title", entry)
			
			// Append player name
			let name = this.appendTextElement(tr, "td", entry)
			name.setAttribute("objective", "Player")
			name.setAttribute("value", entry)
			
			// Prepend online/afk status
			let status = this.prependElement(name, "div")
			status.classList.add("status")
			
			if(data.online[entry] === true){
				status.classList.add("online")
				status.setAttribute("title", "online")
			}else if(data.online[entry] === "afk"){
				status.classList.add("afk")
				status.setAttribute("title", "afk")
			}else{
				status.setAttribute("title", "offline")
			}
			
			// Append empty elements for alignment
			for(const objective of data.scoreboard.objectives){
				let td = this.appendElement(tr, "td")
				td.classList.add("empty")
				td.setAttribute("objective", Display.quoteEscape(objective))
			}
			this.table.append(tr)
		}
		
		// Fill entries
		for(const objective of data.scoreboard.objectives){
			const entries = data.scoreboard.scores[objective]
			for(const entry in entries){
				let td = this.table.querySelector("tr[entry='"
					+ Display.quoteEscape(entry) + "'] td[objective='"
					+ Display.quoteEscape(objective) + "']")
				if(!td) continue
				td.classList.remove("empty")
				td.setAttribute("value", entries[entry])
				td.innerText = Number(entries[entry]).toLocaleString()
			}
		}
	}
	
	updateOnlineStatus(data){
		const rows = this.table.querySelectorAll("tr")
		for(const row of rows){
			const entry = row.getAttribute("entry")
			const statusElement = row.querySelector(".status")
			if(statusElement){
				statusElement.classList.toggle("online", data[entry] === true)
				statusElement.classList.toggle("afk", data[entry] === "afk")
			}
		}
	}
	
	appendElement(base, type){
		let el = document.createElement(type)
		base.append(el)
		return el
	}
	
	prependElement(base, type){
		let el = document.createElement(type)
		base.prepend(el)
		return el
	}
	
	appendTextElement(base, type, name){
		let el = this.appendElement(base, type)
		el.innerText = name
		return el
	}
	
	appendTh(base, name){
		let th = this.appendTextElement(base, "th", name)
		th.onclick = this.thClick.bind(this)
		return th
	}
	
	appendImg(base, src){
		let img = this.appendElement(base, "img")
		img.src = src
		return img
	}
	
	// Sort a HTML table element
	sort(by, descending){
		// Get table rows and sort
		let rows = Array.from(this.table.querySelectorAll("tr"))
		let header = rows.shift() // Don't sort header with data
		rows = rows.sort((a, b) =>
			Display.compare(descending,
				a.querySelector("td[objective='" + Display.quoteEscape(by) + "']").getAttribute("value") ?? "",
				b.querySelector("td[objective='" + Display.quoteEscape(by) + "']").getAttribute("value") ?? ""
			))
		
		// Replace table contents with sorted variant
		this.table.innerHTML = ""
		this.table.append(header)
		for(const row of rows){
			this.table.append(row)
		}
	}
	
	// When a table header is clicked, sort by that header
	thClick(e){
		let objective = e.target.innerText
		this.descending = (objective === this.sortBy) ? !this.descending : true
		this.sortBy = objective
		this.sort(objective, this.descending)
		
		// Set URL query string, for sharing
		window.history.replaceState({}, "",
			location.pathname + "?sort=" + this.sortBy.replace(/\s/g, "+")
			+ "&order=" + (this.descending ? "desc" : "asc"))
	}
	
	// Replace single quotes by '&quot;' (html-escape)
	static quoteEscape = string => string.replace(/'/g, "&quot;")
	
	// When a and b are both numbers, compare as numbers. Otherwise, case-insensitive compare as string
	static compare(descending, a, b){
		if(Number(a) && Number(b)){
			return (descending ? -1 : 1) * (a - b)
		}else{
			return (descending ? -1 : 1) * a.localeCompare(b, undefined, {sensitivity: "base"})
		}
	}
	
}