extends Node3D
## LEGIÕES 3D — INVASÃO. Jogo nativo original Godot 4.4.
## Modelos rigados e animações KayKit CC0; nunca são usados recursos do APK comercial.

const TEAM_BLUE := 0
const TEAM_RED := 1
const MAX_BLUE := 75
const MAX_RED := 28
const STAGES := [
    {"name":"VALE DOS HERÓIS", "ground":Color("65b877"),"lane":Color("6965b9"),"edge":Color("ece3ce"),"bg":Color("a6ddfb"),"accent":Color("37e2ee"),"enemy":Color("ef5066"),"deco":0,"hp":14},
    {"name":"CÂNION DOURADO", "ground":Color("d99864"),"lane":Color("83647e"),"edge":Color("e9b98b"),"bg":Color("fbd9a4"),"accent":Color("ffcb65"),"enemy":Color("ee5f53"),"deco":1,"hp":20},
    {"name":"PONTE GLACIAL", "ground":Color("d8eef7"),"lane":Color("6fa4c3"),"edge":Color("edfaff"),"bg":Color("a9dafa"),"accent":Color("5eeaff"),"enemy":Color("dc557f"),"deco":2,"hp":28},
    {"name":"CIDADE NÉON", "ground":Color("2c3a61"),"lane":Color("454273"),"edge":Color("54e2f8"),"bg":Color("6086b9"),"accent":Color("35ecdc"),"enemy":Color("f66b90"),"deco":3,"hp":37},
    {"name":"FORTALEZA VULCÂNICA", "ground":Color("50353e"),"lane":Color("555368"),"edge":Color("ffa849"),"bg":Color("da8e84"),"accent":Color("ffd462"),"enemy":Color("ff5669"),"deco":4,"hp":50}
]

var rng := RandomNumberGenerator.new()
var stage := 0
var state := "menu"
var time_running := 0.0
var cannon_x := 0.0
var held := false
var fire_clock := 0.0
var enemy_clock := 0.0
var fortress_hp := 15
var player_hp := 10
var coins := 0
var blue_units: Array[Dictionary] = []
var red_units: Array[Dictionary] = []
var particles: Array[Dictionary] = []
var materials := {}
var mesh_cache := {}
var world_root: Node3D
var camera: Camera3D
var environment: WorldEnvironment
var ui: Control
var pop: ColorRect
var pop_title: Label
var pop_text: Label
var pop_button: Button
var hud_stage: Label
var hud_count: Label
var hud_enemy: Label
var hud_health: Label
var hud_coin: Label
var bar: ProgressBar
var banner: Label
var cannon_root: Node3D
var knight_scene: PackedScene
var rogue_scene: PackedScene
var sounds := {}
var sound_players := {}

func _ready() -> void:
    rng.randomize()
    stage = clampi(int(_load_data("stage", 0)), 0, 4)
    coins = maxi(0, int(_load_data("coins", 0)))
    knight_scene = load("res://assets/Knight.glb") as PackedScene
    rogue_scene = load("res://assets/Rogue.glb") as PackedScene
    _audio_setup()
    _make_camera()
    _make_ui()
    _make_world()
    _set_overlay("LEGIOES 3D", "INVASAO  •  CINCO CAMPANHAS\nComande as tropas, multiplique os soldados\ne conquiste as fortalezas inimigas.", "JOGAR AGORA")
    set_process(true)

func _load_data(key: String, fallback: Variant) -> Variant:
    var cfg = ConfigFile.new()
    if cfg.load("user://progress.cfg") != OK:
        return fallback
    return cfg.get_value("save", key, fallback)

func _save_data() -> void:
    var cfg = ConfigFile.new()
    cfg.set_value("save", "stage", stage)
    cfg.set_value("save", "coins", coins)
    cfg.save("user://progress.cfg")

func _make_camera() -> void:
    environment = WorldEnvironment.new()
    var env = Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color("a6ddfb")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("ffffff")
    env.ambient_light_energy = 0.75
    env.reflected_light_source = Environment.REFLECTION_SOURCE_BG
    env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
    environment.environment = env
    add_child(environment)
    camera = Camera3D.new()
    camera.fov = 51.0
    camera.near = 0.1
    camera.far = 150.0
    camera.position = Vector3(0, 28.0, 24.0)
    camera.look_at(Vector3(0, 0, -15.0), Vector3.UP)
    camera.current = true
    add_child(camera)
    var sun = DirectionalLight3D.new()
    sun.rotation_degrees = Vector3(-50, -35, 8)
    sun.light_color = Color("fff4d8")
    sun.light_energy = 1.2
    sun.shadow_enabled = true
    sun.directional_shadow_max_distance = 64.0
    add_child(sun)

func _mat(color: Color, metallic: float = 0.0, emission: float = 0.0) -> StandardMaterial3D:
    var key = str(color.to_html()) + ":" + str(metallic) + ":" + str(emission)
    if materials.has(key):
        return materials[key]
    var m = StandardMaterial3D.new()
    m.albedo_color = color
    m.metallic = metallic
    m.roughness = 0.47 if metallic > 0.05 else 0.76
    if emission > 0.0:
        m.emission_enabled = true
        m.emission = color
        m.emission_energy_multiplier = emission
    materials[key] = m
    return m

func _shape(kind: String, size: Vector3) -> PrimitiveMesh:
    var key = kind + str(size)
    if mesh_cache.has(key):
        return mesh_cache[key]
    var m: PrimitiveMesh
    if kind == "sphere":
        var s = SphereMesh.new()
        s.radius = 0.5
        s.height = 1.0
        s.radial_segments = 10
        s.rings = 6
        m = s
    elif kind == "cylinder":
        var cy = CylinderMesh.new()
        cy.top_radius = 0.5
        cy.bottom_radius = 0.5
        cy.height = 1.0
        cy.radial_segments = 12
        m = cy
    elif kind == "cone":
        var cn = CylinderMesh.new()
        cn.top_radius = 0.0
        cn.bottom_radius = 0.5
        cn.height = 1.0
        cn.radial_segments = 9
        m = cn
    else:
        var box = BoxMesh.new()
        box.size = Vector3.ONE
        m = box
    mesh_cache[key] = m
    return m

func _piece(parent: Node3D, kind: String, at: Vector3, size: Vector3, color: Color, metallic: float = 0.0, glow: float = 0.0) -> MeshInstance3D:
    var p = MeshInstance3D.new()
    p.mesh = _shape(kind, size)
    p.material_override = _mat(color, metallic, glow)
    p.position = at
    p.scale = size
    parent.add_child(p)
    return p

func _label_3d(text: String, pos: Vector3, color: Color, size: int = 78) -> Label3D:
    var label = Label3D.new()
    label.text = text
    label.position = pos
    label.font_size = size
    label.pixel_size = 0.009
    label.modulate = color
    label.outline_modulate = Color("15233a")
    label.outline_size = 9
    label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
    world_root.add_child(label)
    return label

func _make_world() -> void:
    blue_units.clear()
    red_units.clear()
    particles.clear()
    if is_instance_valid(world_root):
        world_root.queue_free()
    world_root = Node3D.new()
    world_root.name = "Campanha"
    add_child(world_root)
    var sd = STAGES[stage]
    environment.environment.background_color = sd.bg
    fortress_hp = int(sd.hp)
    player_hp = 10
    cannon_x = 0.0
    time_running = 0.0
    fire_clock = 0.0
    enemy_clock = 0.8
    camera.position = Vector3(0, 28, 24)
    camera.look_at(Vector3(0, 0, -15), Vector3.UP)
    _piece(world_root, "box", Vector3(0, -0.80, -14), Vector3(120, 1.4, 100), sd.ground)
    _piece(world_root, "box", Vector3(0, -0.12, -12), Vector3(9.7, 0.30, 60), sd.lane)
    _piece(world_root, "box", Vector3(-4.95, -0.02, -12), Vector3(.28, .34, 60), sd.edge)
    _piece(world_root, "box", Vector3(4.95, -0.02, -12), Vector3(.28, .34, 60), sd.edge)
    for z in range(-41, 17, 4):
        _piece(world_root, "box", Vector3(0, 0.055, z), Vector3(.10, .02, 1.6), Color("e6e8ec"))
    for z in range(-41, 17, 6):
        _piece(world_root, "box", Vector3(-4.68, 0.06, z), Vector3(.12, .06, 3.5), Color("c3cbe3"))
        _piece(world_root, "box", Vector3(4.68, 0.06, z), Vector3(.12, .06, 3.5), Color("c3cbe3"))
    for i in range(27):
        var z = 15.0 - float(i) * 2.2
        var x = 7.2 + rng.randf_range(-2.5, 4.2)
        _make_deco(Vector3(x, 0, z), int(sd.deco), i)
        _make_deco(Vector3(-x, 0, z - 0.9), int(sd.deco), i + 31)
    _create_gate(-7.5, [2, 3 + stage % 2], 0)
    _create_gate(-22.0, [2 + floori(stage / 2.0), 3], 1)
    _create_fortress()
    _create_cannon()
    _label_3d("INVASAO   •   " + str(stage + 1), Vector3(0, 0.3, 13), Color("ffffff"), 58)
    for i in range(5):
        _spawn_unit(TEAM_BLUE, rng.randf_range(-0.9, .9), 2.3 + i * .9, true)
    for i in range(3 + stage):
        _spawn_unit(TEAM_RED, rng.randf_range(-2, 2), -34 - i * 1.2, true)
    _refresh_hud()

func _make_deco(at: Vector3, biome: int, seed: int) -> void:
    var x = at.x
    var z = at.z
    var r = rng.randf_range(0.77, 1.26)
    if biome == 0:
        _piece(world_root, "cylinder", Vector3(x, 0.85 * r, z), Vector3(.32 * r, 1.9 * r, .32 * r), Color("8b644f"))
        _piece(world_root, "sphere", Vector3(x, 2.05 * r, z), Vector3(1.65 * r, 2.0 * r, 1.65 * r), Color("389766"))
        _piece(world_root, "sphere", Vector3(x - .5, 2.3 * r, z + .2), Vector3(1.0 * r, 1.25 * r, 1.0 * r), Color("57c889"))
        if seed % 5 == 0: _piece(world_root, "sphere", Vector3(x + 1, .3, z + 1), Vector3(1.4, .7, 1.2), Color("e0d7b7"))
    elif biome == 1:
        _piece(world_root, "cylinder", Vector3(x, 1.4 * r, z), Vector3(.48 * r, 2.8 * r, .48 * r), Color("4b9475"))
        _piece(world_root, "box", Vector3(x - .5 * r, 1.8 * r, z), Vector3(1.1 * r, .35 * r, .4 * r), Color("4b9475"))
        _piece(world_root, "box", Vector3(x - .98 * r, 2.2 * r, z), Vector3(.35 * r, .95 * r, .4 * r), Color("4b9475"))
        _piece(world_root, "sphere", Vector3(x + 1.2 * r, .46, z + 1), Vector3(1.9 * r, .9 * r, 1.7 * r), Color("b95f52"))
    elif biome == 2:
        _piece(world_root, "cylinder", Vector3(x, .95 * r, z), Vector3(.35 * r, 1.9 * r, .35 * r), Color("6f7286"))
        _piece(world_root, "cone", Vector3(x, 2.2 * r, z), Vector3(2.1 * r, 3.7 * r, 2.1 * r), Color("337f94"))
        _piece(world_root, "cone", Vector3(x, 2.75 * r, z), Vector3(1.65 * r, 2.5 * r, 1.65 * r), Color("b2e0ea"))
        if seed % 4 == 0: _piece(world_root, "sphere", Vector3(x + 1.6, .23, z), Vector3(1.0, .46, 1.1), Color("ffffff"))
    elif biome == 3:
        _piece(world_root, "box", Vector3(x, 1.4 * r, z), Vector3(1.5 * r, 2.8 * r, 1.7 * r), Color("35465b"), .3)
        _piece(world_root, "box", Vector3(x, 3.0 * r, z), Vector3(1.9 * r, .16, 1.9 * r), Color("3de2ef"), .4, 1.5)
        for j in range(3):
            _piece(world_root, "box", Vector3(x - .76 * r, (j + .7) * r, z), Vector3(.045, .19, .8), Color("ff8bca"), .1, 1.6)
    else:
        _piece(world_root, "sphere", Vector3(x, .55 * r, z), Vector3(2.2 * r, 1.1 * r, 1.8 * r), Color("322f3c"))
        _piece(world_root, "cone", Vector3(x, 1.35 * r, z), Vector3(.92 * r, 2.3 * r, .95 * r), Color("5d4651"))
        _piece(world_root, "sphere", Vector3(x + .5, .35, z + .4), Vector3(.8, .34, .7), Color("ff682f"), .0, 1.0)

func _create_gate(z: float, mult: Array, gate_index: int) -> void:
    for side in range(2):
        var x = -2.4 if side == 0 else 2.4
        var col = Color("31ddff") if side == 0 else Color("57f2b5")
        _piece(world_root, "box", Vector3(x - 1.9, 1.55, z), Vector3(.26, 3.2, .32), col, .4, .9)
        _piece(world_root, "box", Vector3(x + 1.9, 1.55, z), Vector3(.26, 3.2, .32), col, .4, .9)
        _piece(world_root, "box", Vector3(x, 3.26, z), Vector3(4.2, .37, .42), col, .35, .8)
        _piece(world_root, "box", Vector3(x, .0, z), Vector3(3.9, .10, .62), col, .4, 1.6)
        _label_3d("x" + str(mult[side]), Vector3(x, 3.77, z), Color.WHITE, 77)
        _piece(world_root, "sphere", Vector3(x - 1.9, 3.45, z), Vector3(.5, .5, .5), Color("ffffff"), .1, 1.9)

func _create_fortress() -> void:
    var red = STAGES[stage].enemy
    _piece(world_root, "box", Vector3(0, .50, -41), Vector3(10.5, 1.1, 7.8), Color("484359"))
    _piece(world_root, "box", Vector3(0, 2.25, -42), Vector3(9.7, 3.6, 3.8), Color("a09ab2"))
    _piece(world_root, "box", Vector3(0, 3.35, -39.95), Vector3(2.1, 2.0, .24), Color("543b4b"))
    for side in [-1, 1]:
        _piece(world_root, "cylinder", Vector3(side * 4.4, 3.15, -40.5), Vector3(2.3, 6.3, 2.3), Color("7c718d"))
        _piece(world_root, "cone", Vector3(side * 4.4, 6.9, -40.5), Vector3(3.2, 1.6, 3.2), red)
        _piece(world_root, "cylinder", Vector3(side * 4.4, 7.9, -40.5), Vector3(.08, 1.8, .08), Color("f8e2aa"), .2)
        _piece(world_root, "box", Vector3(side * 4.4 + .52, 8.3, -40.5), Vector3(1.0, .66, .07), red)
    for i in range(7):
        _piece(world_root, "box", Vector3(-3.8 + i * 1.26, 4.25, -40), Vector3(.84, 1.1, .9), Color("c2b7be"))
    _piece(world_root, "sphere", Vector3(0, 5.15, -40.2), Vector3(1.3, 1.3, 1.3), Color("f0b870"), .4, .9)
    _label_3d("FORTALEZA", Vector3(0, 8.2, -40), Color("fff0e9"), 77)

func _create_cannon() -> void:
    cannon_root = Node3D.new()
    cannon_root.position = Vector3(0, 0, 11.8)
    world_root.add_child(cannon_root)
    _piece(cannon_root, "cylinder", Vector3(0, .40, 0), Vector3(2.7, .82, 2.7), Color("264d80"), .35)
    _piece(cannon_root, "cylinder", Vector3(0, .85, -.18), Vector3(1.84, 1.0, 1.84), Color("5ac5ed"), .25)
    _piece(cannon_root, "sphere", Vector3(0, 1.1, -.45), Vector3(1.45, 1.25, 1.65), Color("b7f1ff"), .4)
    _piece(cannon_root, "cylinder", Vector3(0, 1.25, -1.45), Vector3(.70, 3.2, .70), Color("285777"), .55).rotation_degrees.x = 90
    _piece(cannon_root, "cylinder", Vector3(0, 1.25, -2.85), Vector3(.94, .42, .94), Color("95ebff"), .6).rotation_degrees.x = 90
    _piece(cannon_root, "sphere", Vector3(-1.15, .33, .6), Vector3(.75, .8, .75), Color("26354e"), .2)
    _piece(cannon_root, "sphere", Vector3(1.15, .33, .6), Vector3(.75, .8, .75), Color("26354e"), .2)

func _low_soldier(parent: Node3D, team: int) -> Node3D:
    var pivot = Node3D.new()
    parent.add_child(pivot)
    var suit = Color("258af4") if team == TEAM_BLUE else Color("e64660")
    var light = Color("47cbff") if team == TEAM_BLUE else Color("ff8490")
    _piece(pivot, "sphere", Vector3(0, 1.02, 0), Vector3(.52, .72, .40), suit, .16)
    _piece(pivot, "sphere", Vector3(0, 1.68, 0), Vector3(.42, .42, .41), Color("efc6a2"))
    _piece(pivot, "sphere", Vector3(0, 1.92, -.04), Vector3(.48, .22, .43), light, .3)
    _piece(pivot, "box", Vector3(0, 1.12, -.31), Vector3(.40, .18, .12), Color("d8f4fb"), .4)
    for d in [-1, 1]:
        _piece(pivot, "sphere", Vector3(float(d) * .40, 1.12, -.01), Vector3(.25, .60, .26), suit)
        _piece(pivot, "sphere", Vector3(float(d) * .18, .45, .02), Vector3(.23, .75, .23), Color("34475f"))
    return pivot

func _spawn_unit(team: int, x: float, z: float, preview: bool = false) -> void:
    var list = blue_units if team == TEAM_BLUE else red_units
    if list.size() >= (MAX_BLUE if team == TEAM_BLUE else MAX_RED):
        return
    var actor = Node3D.new()
    world_root.add_child(actor)
    actor.position = Vector3(x, 0.06, z)
    var model: Node3D
    # Personagens reais GLB rigados no primeiro plano, LOD barato para multidões maiores.
    var detailed = list.size() < (19 if team == TEAM_BLUE else 12)
    if detailed and (knight_scene != null if team == TEAM_BLUE else rogue_scene != null):
        var scene = knight_scene if team == TEAM_BLUE else rogue_scene
        model = scene.instantiate() as Node3D
        actor.add_child(model)
        model.scale = Vector3(.86, .86, .86)
        model.rotation.y = PI if team == TEAM_BLUE else 0.0
        var anim: AnimationPlayer = model.find_child("AnimationPlayer", true, false) as AnimationPlayer
        if anim != null:
            for anim_name in anim.get_animation_list():
                if str(anim_name).to_lower().contains("running") or str(anim_name).to_lower().contains("walk"):
                    anim.play(anim_name)
                    break
        var cc = Color("21b9ff") if team == TEAM_BLUE else Color("ff526a")
        _piece(actor, "cylinder", Vector3(0, .03, 0), Vector3(.75, .06, .75), cc, .1, 1.15)
    else:
        model = _low_soldier(actor, team)
        model.scale = Vector3(.72, .72, .72)
        model.rotation.y = PI if team == TEAM_BLUE else 0.0
    var unit = {"node":actor, "model":model, "z":z, "x":x, "age":rng.randf_range(0, 6.0), "gates":0, "preview":preview}
    list.append(unit)

func _remove_unit(list: Array[Dictionary], at: int) -> void:
    var d = list[at]
    if is_instance_valid(d.node):
        d.node.queue_free()
    list.remove_at(at)

func _burst(pos: Vector3, team: int) -> void:
    var col = Color("65e6ff") if team == TEAM_BLUE else Color("ffbd57")
    _play_sound("hit")
    for i in range(6):
        var p = _piece(world_root, "sphere", pos + Vector3(0, .85, 0), Vector3(.22, .22, .22), col, .1, 1.0)
        particles.append({"node":p, "vel":Vector3(rng.randf_range(-3, 3),rng.randf_range(2, 5),rng.randf_range(-3,3)), "time":.5})

func _process(delta: float) -> void:
    var dt = minf(delta, .045)
    if state != "playing":
        return
    time_running += dt
    if held:
        fire_clock -= dt
        if fire_clock <= 0.0:
            fire_clock = .19 - .015 * float(stage)
            if blue_units.size() < MAX_BLUE:
                _spawn_unit(TEAM_BLUE, cannon_x + rng.randf_range(-.20, .20), 8.45)
                _play_sound("shot")
    cannon_root.position.x = lerpf(cannon_root.position.x, cannon_x, minf(1.0, dt * 16))
    enemy_clock -= dt
    if enemy_clock <= 0 and red_units.size() < MAX_RED:
        enemy_clock = maxf(.7, 2.1 - stage * .22)
        for i in range(1 + floori(stage / 2.0)):
            _spawn_unit(TEAM_RED, rng.randf_range(-3.7, 3.7), -35.0 - rng.randf_range(0,2.0))
    var spawns: Array[Vector3] = []
    for i in range(blue_units.size() - 1, -1, -1):
        var soldier = blue_units[i]
        var old_z = float(soldier.z)
        soldier.z = old_z - dt * (7.9 + stage * .18)
        soldier.age = float(soldier.age) + dt
        soldier.node.position = Vector3(float(soldier.x), .06 + sin(float(soldier.age) * 10.5) * .045, float(soldier.z))
        if not soldier.preview:
            for gate_index in range(2):
                var gate_z = -7.5 if gate_index == 0 else -22.0
                var mask = 1 << gate_index
                if old_z >= gate_z and float(soldier.z) < gate_z and int(soldier.gates) & mask == 0:
                    soldier.gates = int(soldier.gates) | mask
                    var factor = (2 if float(soldier.x) < 0 else 3 + stage % 2) if gate_index == 0 else (2 + floori(stage / 2.0) if float(soldier.x) < 0 else 3)
                    for extra in range(factor - 1):
                        if blue_units.size() + spawns.size() < MAX_BLUE:
                            spawns.append(Vector3(clampf(float(soldier.x) + rng.randf_range(-.5,.5), -4,4), float(soldier.z) - .45, float(mask)))
                    _play_sound("portal")
        if float(soldier.z) < -38.5:
            fortress_hp -= 1
            _burst(soldier.node.position, TEAM_BLUE)
            _remove_unit(blue_units, i)
            if fortress_hp <= 0:
                _victory()
                return
    for extra in spawns:
        _spawn_unit(TEAM_BLUE, extra.x, extra.y)
        # Clone atravessou esse portal; não atravessa o mesmo duas vezes.
        if blue_units.size() > 0:
            blue_units[-1].gates = int(extra.z)
    for i in range(red_units.size() - 1, -1, -1):
        var foe = red_units[i]
        foe.z = float(foe.z) + dt * (3.7 + stage * .32)
        foe.age = float(foe.age) + dt
        foe.node.position = Vector3(float(foe.x), .06 + sin(float(foe.age) * 10.0) * .04, float(foe.z))
        if float(foe.z) > 9.2:
            player_hp -= 1
            _burst(foe.node.position, TEAM_RED)
            _remove_unit(red_units, i)
            if player_hp <= 0:
                _defeat()
                return
    for i in range(blue_units.size() - 1, -1, -1):
        if i >= blue_units.size():
            continue
        var a = blue_units[i]
        var killed = false
        for j in range(red_units.size() - 1, -1, -1):
            var b = red_units[j]
            var dx = float(a.x) - float(b.x)
            var dz = float(a.z) - float(b.z)
            if dx * dx + dz * dz < 0.75:
                _burst(a.node.position, TEAM_RED)
                _remove_unit(red_units, j)
                killed = true
                coins += 1
                break
        if killed:
            _remove_unit(blue_units, i)
    for i in range(particles.size() - 1, -1, -1):
        var p = particles[i]
        p.time = float(p.time) - dt
        p.node.position += p.vel * dt
        p.vel.y = float(p.vel.y) - dt * 14.0
        if float(p.time) <= 0.0:
            p.node.queue_free()
            particles.remove_at(i)
    _refresh_hud()

func _victory() -> void:
    state = "victory"
    held = false
    coins += 50 + stage * 20
    stage = (stage + 1) % 5
    _save_data()
    _play_sound("win")
    _set_overlay("FORTALEZA CONQUISTADA!", "Vitoria! +" + str(50 + (stage + 4) % 5 * 20) + " moedas\nProxima campanha desbloqueada.", "PROXIMA FASE  >")

func _defeat() -> void:
    state = "defeat"
    held = false
    _save_data()
    _play_sound("lose")
    _set_overlay("BASE SOB ATAQUE!", "Suas tropas precisam de mais reforcos.\nPasse pelos multiplicadores maiores\ne proteja o canhao.", "TENTAR DE NOVO")

func _begin() -> void:
    if state == "playing":
        return
    var was_victory = state == "victory"
    _make_world()
    state = "playing"
    held = false
    pop.visible = false
    banner.text = "SEGURE PARA DISPARAR   •   ARRASTE PARA MIRAR"
    if was_victory:
        _play_sound("portal")
    _refresh_hud()

func _unhandled_input(event: InputEvent) -> void:
    if state != "playing":
        return
    if event is InputEventScreenTouch:
        held = event.pressed
        cannon_x = _aim_from_x(event.position.x)
    elif event is InputEventScreenDrag:
        held = true
        cannon_x = _aim_from_x(event.position.x)
    elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
        held = event.pressed
        cannon_x = _aim_from_x(event.position.x)
    elif event is InputEventMouseMotion and held:
        cannon_x = _aim_from_x(event.position.x)
    elif event is InputEventKey:
        if event.keycode == KEY_LEFT:
            cannon_x = maxf(-3.8, cannon_x - .35)
            held = event.pressed
        elif event.keycode == KEY_RIGHT:
            cannon_x = minf(3.8, cannon_x + .35)
            held = event.pressed
        elif event.keycode == KEY_SPACE:
            held = event.pressed

func _aim_from_x(pixel: float) -> float:
    var width = get_viewport().get_visible_rect().size.x
    if width <= 0.0:
        return cannon_x
    return clampf((pixel / width - .5) * 9.0, -3.9, 3.9)

func _box_style(bg: Color, stroke: Color = Color("ffffff")) -> StyleBoxFlat:
    var sb = StyleBoxFlat.new()
    sb.bg_color = bg
    sb.border_color = stroke
    sb.set_border_width_all(2)
    sb.corner_radius_top_left = 18
    sb.corner_radius_top_right = 18
    sb.corner_radius_bottom_left = 18
    sb.corner_radius_bottom_right = 18
    sb.content_margin_left = 17
    sb.content_margin_right = 17
    sb.content_margin_top = 13
    sb.content_margin_bottom = 13
    sb.shadow_color = Color(0, 0, 0, .35)
    sb.shadow_size = 8
    return sb

func _ui_label(text: String, size: int, color: Color = Color.WHITE) -> Label:
    var label = Label.new()
    label.text = text
    label.add_theme_font_size_override("font_size", size)
    label.add_theme_color_override("font_color", color)
    label.add_theme_color_override("font_shadow_color", Color(0,0,.1,.8))
    label.add_theme_constant_override("shadow_offset_x", 1)
    label.add_theme_constant_override("shadow_offset_y", 2)
    return label

func _make_ui() -> void:
    var layer = CanvasLayer.new()
    add_child(layer)
    ui = Control.new()
    ui.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    ui.mouse_filter = Control.MOUSE_FILTER_IGNORE
    layer.add_child(ui)
    var margin = MarginContainer.new()
    margin.set_anchors_preset(Control.PRESET_TOP_WIDE)
    margin.offset_top = 12
    margin.offset_bottom = 110
    margin.mouse_filter = Control.MOUSE_FILTER_IGNORE
    margin.add_theme_constant_override("margin_left", 14)
    margin.add_theme_constant_override("margin_right", 14)
    ui.add_child(margin)
    var stack = VBoxContainer.new()
    stack.add_theme_constant_override("separation", 7)
    margin.add_child(stack)
    var top = HBoxContainer.new()
    top.add_theme_constant_override("separation", 8)
    stack.add_child(top)
    var left = PanelContainer.new()
    left.add_theme_stylebox_override("panel", _box_style(Color("11233bd9"), Color("57c5ed")))
    left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    top.add_child(left)
    var info = VBoxContainer.new()
    left.add_child(info)
    hud_stage = _ui_label("FASE 1/5", 20)
    info.add_child(hud_stage)
    hud_coin = _ui_label("◈ 000", 15, Color("ffe291"))
    info.add_child(hud_coin)
    var right = PanelContainer.new()
    right.add_theme_stylebox_override("panel", _box_style(Color("11233bd9"), Color("ffb86b")))
    right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    top.add_child(right)
    var ri = VBoxContainer.new()
    right.add_child(ri)
    hud_health = _ui_label("BASE 10   |   FORTE 14", 15)
    ri.add_child(hud_health)
    hud_count = _ui_label("AZUIS 0", 16, Color("70e9ff"))
    ri.add_child(hud_count)
    hud_enemy = _ui_label("VERMELHOS 0", 14, Color("ff8f98"))
    ri.add_child(hud_enemy)
    bar = ProgressBar.new()
    bar.custom_minimum_size.y = 13
    bar.show_percentage = false
    bar.max_value = 50
    bar.value = 0
    stack.add_child(bar)
    var foot = PanelContainer.new()
    foot.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
    foot.offset_left = 12
    foot.offset_right = -12
    foot.offset_top = -88
    foot.offset_bottom = -15
    foot.add_theme_stylebox_override("panel", _box_style(Color("0c2946d9"), Color("30b6d3")))
    foot.mouse_filter = Control.MOUSE_FILTER_IGNORE
    ui.add_child(foot)
    banner = _ui_label("SEGURE PARA DISPARAR   •   ARRASTE PARA MIRAR", 16, Color("d5f7ff"))
    banner.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    banner.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    banner.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    foot.add_child(banner)
    pop = ColorRect.new()
    pop.color = Color(.02, .05, .13, .68)
    pop.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    pop.mouse_filter = Control.MOUSE_FILTER_IGNORE
    ui.add_child(pop)
    var center = CenterContainer.new()
    center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    center.mouse_filter = Control.MOUSE_FILTER_IGNORE
    pop.add_child(center)
    var card = PanelContainer.new()
    card.custom_minimum_size = Vector2(460, 335)
    card.mouse_filter = Control.MOUSE_FILTER_IGNORE
    card.add_theme_stylebox_override("panel", _box_style(Color("0e2445f7"), Color("47cafa")))
    center.add_child(card)
    var rows = VBoxContainer.new()
    rows.alignment = BoxContainer.ALIGNMENT_CENTER
    rows.add_theme_constant_override("separation", 22)
    card.add_child(rows)
    pop_title = _ui_label("LEGIOES 3D", 31, Color("69e8ff"))
    pop_title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    rows.add_child(pop_title)
    pop_text = _ui_label("INVASAO  •  CINCO CAMPANHAS", 17, Color("e0f1fc"))
    pop_text.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    pop_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    rows.add_child(pop_text)
    pop_button = Button.new()
    pop_button.text = "JOGAR AGORA"
    pop_button.custom_minimum_size = Vector2(0, 68)
    pop_button.add_theme_font_size_override("font_size", 22)
    pop_button.add_theme_color_override("font_color", Color("ffffff"))
    pop_button.add_theme_stylebox_override("normal", _box_style(Color("0b9fc7"), Color("6df2ff")))
    pop_button.add_theme_stylebox_override("hover", _box_style(Color("14d5e5"), Color("ffffff")))
    pop_button.add_theme_stylebox_override("pressed", _box_style(Color("057f9b"), Color("ffffff")))
    rows.add_child(pop_button)
    pop_button.pressed.connect(_begin)

func _set_overlay(title: String, message: String, action: String) -> void:
    pop.visible = true
    pop_title.text = title
    pop_text.text = message
    pop_button.text = action

func _refresh_hud() -> void:
    if hud_stage == null:
        return
    hud_stage.text = "FASE " + str(stage + 1) + "/5  •  " + str(STAGES[stage].name)
    hud_coin.text = "◆ " + str(coins) + " MOEDAS"
    hud_health.text = "BASE " + str(maxi(0, player_hp)) + "  |  FORTE " + str(maxi(0, fortress_hp))
    hud_count.text = "AZUIS " + str(blue_units.size())
    hud_enemy.text = "VERMELHOS " + str(red_units.size())
    bar.max_value = maxf(1.0, float(STAGES[stage].hp))
    bar.value = maxf(0.0, float(STAGES[stage].hp) - fortress_hp)

func _audio_setup() -> void:
    sounds["shot"] = _wave(730.0, .085, 0)
    sounds["hit"] = _wave(195.0, .16, 1)
    sounds["portal"] = _wave(505.0, .25, 2)
    sounds["win"] = _wave(500.0, .52, 2)
    sounds["lose"] = _wave(160.0, .36, 1)
    for name in sounds.keys():
        var player = AudioStreamPlayer.new()
        player.stream = sounds[name]
        player.volume_db = -9.0 if name == "shot" else -5.0
        add_child(player)
        sound_players[name] = player

func _wave(freq: float, duration: float, mode: int) -> AudioStreamWAV:
    var rate = 16000
    var count = int(float(rate) * duration)
    var buffer = PackedByteArray()
    buffer.resize(count * 2)
    for i in range(count):
        var t = float(i) / rate
        var progress = float(i) / maxi(1, count - 1)
        var f = freq * (1.3 - progress * .55 if mode == 0 else 1.0 + progress * .65 if mode == 2 else 1.0 - progress * .65)
        var tone = sin(TAU * f * t) * .65 + sin(TAU * (f * .5) * t) * .2
        if mode == 1:
            tone = tone * .3 + rng.randf_range(-.65, .65)
        var sample = clampi(int(tone * pow(1.0 - progress, 1.3) * 14000.0), -32768, 32767)
        buffer.encode_s16(i * 2, sample)
    var wav = AudioStreamWAV.new()
    wav.format = AudioStreamWAV.FORMAT_16_BITS
    wav.mix_rate = rate
    wav.stereo = false
    wav.data = buffer
    return wav

func _play_sound(name: String) -> void:
    if sound_players.has(name):
        var p = sound_players[name] as AudioStreamPlayer
        p.play()