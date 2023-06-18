local redis = require 'redis'
local json = require 'cjson'
local client = redis.connect('192.168.1.202', 6379)

local function get_object(_id, _list_id, _follow)
    local nested = {}
    local r = client:hgetall(_id)
    if _follow then
        nested = client:smembers(r[_list_id])
    end
    if _list_id == nil then
    else
        r[_list_id] = nil
    end
    return r, nested
    end


local mapping = {
    [""] = {
        ["dates"] = {
            ["self"] = "DATES",
            ["type"] = 1
        },
        ["options"] = {
            ["self"] = "OPTIONS",
            ["children"] = "OPTION",
            ["type"] = 2,
        }
    },
    ["OPTIONS"] = {
        ["payouts"] = {
            ["self"] = "PAYOUTS",
            ["children"] = "PAYOUT",
            ["type"] = 2
        }
    }
}

local function is_array(t)
    local i = 0
    for _ in pairs(t) do
        i = i + 1
        if t[i] == nil then return false end
    end
    return true
end

local function populate(key, obj, level)
    for k, v in pairs(obj) do
        if mapping[level] ~= nil and mapping[level][k] ~= nil then
            local new_key = key .. ":" .. mapping[level][k]["self"]
            client:hmset(key, k,new_key)
            if is_array(v) then
                for i, x in ipairs(v) do
                    local inter_key = key .. ":" .. mapping[level][k]["children"] .. ":" .. tostring(i)
                    client:sadd(new_key,inter_key)
                    populate(inter_key,x,mapping[level][k]["self"])
                end
            else
                populate(new_key, v,mapping[level][k]["self"])
            end
        else
            client:hmset(key, k,v)
        end
    end
end

local function get_payouts(pkeys)
    local payouts = {}
    for _, v2 in ipairs(pkeys) do
        local payout, _ = get_object(v2, nil, false)
        table.insert(payouts, payout)
    end
    return payouts
end

local function get_options(key, _payouts)
    local output = {}
    local option_ids = client:smembers(key .. ":" .. "OPTIONS")
    for _, v in ipairs(option_ids) do
        local option, pkeys = get_object(v, "payouts", _payouts)
        if _payouts then
            option["payouts"] = get_payouts(pkeys)
        end
        table.insert(output, option)
    end
    return json.encode(output)
end

local function query_object(obj, level, keys)
    if mapping[level] ~= nil then
        for k, _ in pairs(mapping[level]) do
            local obj_map = mapping[level][k]
            if obj[k] ~= nil then
                if obj_map["type"] == 1 then
                    local o = client:hgetall(obj[k])
                    query_object(o, obj_map["self"], keys)
                    table.insert(keys,obj[k])
                elseif obj_map["type"] == 2 then
                    local arr = client:smembers(obj[k])
                    table.insert(keys,obj[k])
                    for _, key in ipairs(arr) do
                        table.insert(keys,key)
                        local o = client:hgetall(key)
                        query_object(o, obj_map["self"], keys)
                    end
                end

            end
        end
    end
end

local function delete(key)
    local keys = {key}
    local obj = client:hgetall(key)
    query_object(obj,"",keys)
    for _, o in ipairs(keys) do
        client:del(o)
    end
end

--function set_json()

local nid = "ID1234"
local input = '{"prop1":"val1","prop2":"val2","dates":{"a":"1234","b":"5678"},"options":[{"payouts":[{"payout_prop2":"payout_val2","payout_prop1":"payout_val1"},{"payout_prop2":"payout_val2","payout_prop1":"payout_val1"}],"option_prop3":"option_val3","option_prop1":"option_val1","option_prop2":"option_val2"},{"payouts":[{"payout_prop6":"payout_val6","payout_prop4":"payout_val4"},{"payout_prop3":"payout_val3"}],"option_prop3":"option_val3","option_prop1":"option_val1","option_prop2":"option_val2"}],"prop4":"val4","prop3":"val3"}'
local obj = json.decode(input)
--populate(nid,obj,"")
delete(nid)
--print(get_options("ID1234",true))
--print(get_options("ID1234",false))
--for k, v in pairs(obj) do
--    if k == "options" then
--        local options_key = nid .. ":OPTIONS"
--        client:hmset(nid, k,options_key)
--        for i, x in ipairs(v) do
--            print(i,x)
--        end
--    else
--        client:hmset(nid, k,v)
--    end
--end
--end

